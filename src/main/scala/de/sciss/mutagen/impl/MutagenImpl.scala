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
import de.sciss.processor.GenericProcessor
import de.sciss.processor.impl.ProcessorImpl
import de.sciss.strugatzki.FeatureExtraction
import de.sciss.synth._
import de.sciss.synth.io.AudioFile
import de.sciss.synth.ugen.SampleRate
import de.sciss.topology.Topology

import scala.annotation.tailrec
import scala.collection.immutable.{IndexedSeq => Vec}

final class MutagenImpl(config: Mutagen.Config)
  extends ProcessorImpl[Mutagen.Product, Mutagen.Repr] with GenericProcessor[Mutagen.Product] {

  private val rnd = new util.Random(config.seed)

  protected def body(): Vec[SynthGraph] = {
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

    /*
    // -- 1 --
    // analyze input using Strugatzki
    val spec  = AudioFile.readSpec(config.in)
    require(spec.numChannels == 1, s"Input file '${config.in.name}' must be mono but has ${spec.numChannels} channels")
    val exCfg             = FeatureExtraction.Config()
    exCfg.audioInput      = config.in
    exCfg.featureOutput   = File.createTemp(suffix = ".xml")
    val ex                = FeatureExtraction(exCfg)
    ex.start()
    */

    // -- 2 --
    // generate initial random population
    val pop0  = Vector.fill(config.population)(mkIndividual())

    pop0.map(mkSynthGraph)
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

  // ---- random functions ----
  // cf. https://github.com/Sciss/Dissemination/blob/master/src/main/scala/de/sciss/semi/Util.scala

  private def rrand  (lo: Int   , hi: Int   ): Int    = lo + rnd.nextInt(hi - lo + 1)
  private def exprand(lo: Double, hi: Double): Double = lo * math.exp(math.log(hi / lo) * rnd.nextDouble())

  private def coin(p: Double = 0.5): Boolean = rnd.nextDouble() < p

  private def choose[A](xs: Vec[A]): A = xs(rnd.nextInt(xs.size))

  // ----

  private type Top = Topology[Vertex, Edge]

  def mkConstant(): Vertex.Constant = {
    val f0  = exprand(0.001, 10000.001) - 0.001
    val f   = if (coin(0.25)) -f0 else f0
    val v   = Vertex.Constant(f.toFloat)
    v
  }

  def mkSynthGraph(c: Chromosome): SynthGraph = {
    val top = c.top

    @tailrec def loop(rem: Vec[Vertex], real: Map[Vertex, GE]): Map[Vertex, GE] = rem match {
      case init :+ last =>
        val value: GE = last match {
          case Vertex.Constant(f) => ugen.Constant(f)
          case Vertex.UGen(spec) =>
            val (consValues, consTypes) = spec.args.map { arg =>
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
                    if (x.isEmpty) {
                      println("HERE")
                    }
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
            } .unzip

            val consName = spec.rates.method match {
              case UGenSpec.RateMethod.Alias (name) => name
              case UGenSpec.RateMethod.Custom(name) => name
              case UGenSpec.RateMethod.Default =>
                val rate = spec.rates.set.max
                rate.methodName
            }

            // yes I know, we could use Scala reflection
            val companionName   = s"de.sciss.synth.ugen.${spec.name}$$"
            val companionClass  = Class.forName(companionName)
            val companionMod    = companionClass.getField("MODULE$").get(null)
            val cons            = companionClass.getMethod(consName, consTypes: _*)
            val ge              = cons.invoke(companionMod, consValues: _*).asInstanceOf[GE]
            ge
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
        import ugen._
        val sig0: GE = if (roots.isEmpty) map(choose(ugens)) else Mix(roots.map(map.apply))
        val isOk  = CheckBadValues.ar(sig0) sig_== 0
        val sig1  = Gate.ar(sig0, isOk)
        val sig   = Limiter.ar(LeakDC.ar(sig1))
        Out.ar(0, sig)
      }
    }
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
    new Chromosome(t0)
  }
}
