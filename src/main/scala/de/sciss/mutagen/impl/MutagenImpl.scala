/*
 *  MutagenImpl.scala
 *  (Mutagen)
 *
 *  Copyright (c) 2014 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v2+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.mutagen
package impl

import de.sciss.file._
import de.sciss.lucre.synth.InMemory
import de.sciss.processor.impl.ProcessorImpl
import de.sciss.span.Span
import de.sciss.strugatzki.FeatureExtraction
import de.sciss.synth.{demand, UndefinedRate, UGenSpec, ugen, GE, SynthGraph}
import de.sciss.synth.io.{AudioFileSpec, AudioFile}
import de.sciss.synth.proc.{Timeline, ExprImplicits, Proc, Obj, WorkspaceHandle, Bounce}
import de.sciss.synth.ugen.SampleRate
import de.sciss.topology.Topology
import Util._

import scala.annotation.tailrec
import scala.collection.immutable.{IndexedSeq => Vec}
import scala.concurrent.Future

final class MutagenImpl(config: Mutagen.Config)
  extends Mutagen with ProcessorImpl[Mutagen.Product, Mutagen.Repr] {

  implicit val random = new util.Random(config.seed)

  protected def body(): Vec[Chromosome] = {
    // outline of algorithm:
    // 1. analyze input using Strugatzki
    // 2. generate initial random population
    //
    // 3. render each chromosome using offline server
    // 4. analyze and compare each chromosome's audio output using Strugatzki
    // 5. rank, select, mutate
    // 6. iterate from 3 for number of iterations
    //
    // 7. return resulting population as SynthGraph

    // -- 1 --
    // analyze input using Strugatzki
    val spec  = AudioFile.readSpec(config.in)
    require(spec.numChannels == 1, s"Input file '${config.in.name}' must be mono but has ${spec.numChannels} channels")
    val exCfg             = FeatureExtraction.Config()
    exCfg.audioInput      = config.in
    exCfg.featureOutput   = File.createTemp(suffix = ".aif")
    val ex                = FeatureExtraction(exCfg)
    ex.start()

    // -- 2 --
    // generate initial random population
    val pop0  = Vector.fill(config.population)(mkIndividual())

    pop0
  }

  private def mkSynthGraph(top: Top): SynthGraph = {
    import Util._
    @tailrec def loop(rem: Vec[Vertex], real: Map[Vertex, GE]): Map[Vertex, GE] = rem match {
      case init :+ last =>
        val value: GE = last match {
          case Vertex.Constant(f) => ugen.Constant(f)
          case u @ Vertex.UGen(spec) =>
            val ins = spec.args.map { arg =>
              val res: (AnyRef, Class[_]) = arg.tpe match {
                case UGenSpec.ArgumentType.Int =>
                  val v = arg.defaults.get(UndefinedRate) match {
                    case Some(UGenSpec.ArgumentValue.Int(i)) => i
                    case _ => rrand(1, 2)
                  }
                  (v.asInstanceOf[AnyRef], classOf[Int])

                case UGenSpec.ArgumentType.GE(_, _) =>
                  val inGEOpt = top.edgeMap.getOrElse(last, Set.empty).flatMap { e =>
                    if (e.inlet == arg.name) real.get(e.targetVertex) else None
                  } .headOption
                  val inGE = inGEOpt.getOrElse {
                    val x = arg.defaults.get(UndefinedRate)
                    // if (x.isEmpty) {
                    //   println("HERE")
                    // }
                    x.get /* arg.defaults(UndefinedRate) */ match {
                      case UGenSpec.ArgumentValue.Boolean(v)    => ugen.Constant(if (v) 1 else 0)
                      case UGenSpec.ArgumentValue.DoneAction(v) => ugen.Constant(v.id)
                      case UGenSpec.ArgumentValue.Float(v)      => ugen.Constant(v)
                      case UGenSpec.ArgumentValue.Inf           => ugen.Constant(Float.PositiveInfinity)
                      case UGenSpec.ArgumentValue.Int(v)        => ugen.Constant(v)
                      case UGenSpec.ArgumentValue.Nyquist       => SampleRate.ir / 2
                      case UGenSpec.ArgumentValue.String(v)     => ugen.Escape.stringToGE(v)
                    }
                  }
                  (inGE, classOf[GE])
              }
              res
            }

            u.instantiate(ins)
        }

        loop(init, real + (last -> value))

      case _ =>  real
    }

    SynthGraph {
      val map   = loop(top.vertices, Map.empty)
      val ugens = top.vertices.collect {
        case ugen: Vertex.UGen => ugen
      }
      val roots = ugens.filter { ugen =>
        top.edges.forall(_.targetVertex != ugen)
      }
      if (ugens.nonEmpty) {
        import de.sciss.synth.ugen._
        val sig0: GE = if (roots.isEmpty) map(choose(ugens)) else Mix(roots.map(map.apply))
        val isOk  = CheckBadValues.ar(sig0) sig_== 0
        val sig1  = Gate.ar(sig0, isOk)
        val sig   = Limiter.ar(LeakDC.ar(sig1))
        Out.ar(0, sig)
      }
    }
  }

  private val NoNoAttr: Set[UGenSpec.Attribute] = {
    import UGenSpec.Attribute._
    Set(HasSideEffect, ReadsBuffer, ReadsBus, ReadsFFT, WritesBuffer, WritesBus, WritesFFT)
  }

  private val RemoveUGens = Set[String](
    "MouseX", "MouseY", "MouseButton", "KeyState",
    "BufChannels", "BufDur", "BufFrames", "BufRateScale", "BufSampleRate", "BufSamples",
    "SendTrig", "SendReply", "CheckBadValues",
    "Demand", "DemandEnvGen", "Duty",
    "SubsampleOffset", // "Klang", "Klank", "EnvGen", "IEnvGen"
    "LocalIn" /* for now! */,
    "NumAudioBuses", "NumBuffers", "NumControlBuses", "NumInputBuses", "NumOutputBuses", "NumRunningSynths",
    "Free", "FreeSelf", "FreeSelfWhenDone", "PauseSelf", "PauseSelfWhenDone",
    "ClearBuf", "LocalBuf",
    "RandID", "RandSeed"
  )

  private val ugens: Vec[UGenSpec] = UGenSpec.standardUGens.valuesIterator.filter { spec =>
    spec.attr.intersect(NoNoAttr).isEmpty && !RemoveUGens.contains(spec.name) && spec.outputs.nonEmpty &&
      !spec.rates.set.contains(demand)
  } .toIndexedSeq

  private val constProb       = 0.5
  private val minNumVertices  = 4
  private val maxNumVertices  = 50 // 100
  private val nonDefaultProb  = 0.5

  private type Top = Topology[Vertex, Edge]

  def mkConstant(): Vertex.Constant = {
    val f0  = exprand(0.001, 10000.001) - 0.001
    val f   = if (coin(0.25)) -f0 else f0
    val v   = Vertex.Constant(f.toFloat)
    v
  }

  def evaluate(c: Chromosome, spec: AudioFileSpec): Future[Double] = {
    type S  = InMemory
    implicit val cursor = InMemory()  // XXX TODO - create that once
    val exp = ExprImplicits[S]
    import exp._

    val objH = cursor.step { implicit tx =>
      val proc      = Proc[S]
      proc.graph()  = c.graph
      val procObj   = Obj(Proc.Elem(proc))
      tx.newHandle(procObj)
    }
    import WorkspaceHandle.Implicits._
    val bncCfg                      = Bounce.Config[S]
    bncCfg.group                    = objH :: Nil
    val audioF                      = File.createTemp(suffix = ".aif")
    val duration                    = spec.numFrames.toDouble / spec.sampleRate
    bncCfg.server.nrtOutputPath     = audioF.path
    bncCfg.server.inputBusChannels  = 0
    bncCfg.server.outputBusChannels = 1
    bncCfg.server.sampleRate        = spec.sampleRate.toInt
    // bc.init : (S#Tx, Server) => Unit
    bncCfg.span   = Span(0L, (duration * Timeline.SampleRate).toLong)
    val bnc   = Bounce[S, S].apply(bncCfg)
    bnc.start()

    val featF = File.createTemp(suffix = ".aif")

    val ex = bnc.map { _ =>
      val exCfg             = FeatureExtraction.Config()
      exCfg.audioInput      = audioF
      exCfg.featureOutput   = featF
      val _ex               = FeatureExtraction(exCfg)
      _ex.start()
      _ex
    }

    // XXX TODO: run cross correlation

    ???
  }

  def mkIndividual(): Chromosome = {
    val num = rrand(minNumVertices, maxNumVertices)

    @tailrec def loopGraph(pred: Top): Top =
      if (pred.vertices.size >= num) pred else {
        val next: Top = if (coin(constProb)) {
          val v = mkConstant()
          pred.addVertex(v)

        } else {
          val spec    = choose(ugens)
          val v       = Vertex.UGen(spec)
          //          if (spec.name == "Pitch") {
          //            println("HERE")
          //          }
          val t1      = pred.addVertex(v)
          val geArgs  = spec.args.filter(_.tpe != UGenSpec.ArgumentType.Int)
          val (hasDef, hasNoDef)          = geArgs.partition(_.defaults.contains(UndefinedRate))
          val (useNotDef, _ /* useDef */) = hasDef.partition(_ => coin(nonDefaultProb))
          val findDef = hasNoDef ++ useNotDef

          @tailrec def loopVertex(rem: Vec[UGenSpec.Argument], pred: Top): Top = rem match {
            case head +: tail =>
              val options = pred.vertices.filter { vi =>
                val e = Edge(v, vi, head.name)
                pred.canAddEdge(e)
              }
              val next: Top = if (options.nonEmpty) {
                val vi  = choose(options)
                val e   = Edge(v, vi, head.name)
                pred.addEdge(e).get._1
              } else {
                val vi  = mkConstant()
                val n0  = pred.addVertex(vi)
                val e   = Edge(v, vi, head.name)
                n0.addEdge(e).get._1
              }

              loopVertex(tail, next)

            case _ => pred
          }

          loopVertex(findDef, t1)
        }

        loopGraph(next)
      }

    val t0 = loopGraph(Topology.empty)
    new Chromosome(t0, mkSynthGraph(t0))
  }
}
