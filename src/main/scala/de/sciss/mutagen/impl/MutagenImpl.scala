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

import java.util.concurrent.TimeUnit

import de.sciss.file._
import de.sciss.lucre.synth.InMemory
import de.sciss.processor.impl.ProcessorImpl
import de.sciss.span.Span
import de.sciss.strugatzki.{FeatureCorrelation, FeatureExtraction}
import de.sciss.synth.{demand, UndefinedRate, UGenSpec, ugen, GE, SynthGraph}
import de.sciss.synth.io.{AudioFileSpec, AudioFile}
import de.sciss.synth.proc.{Timeline, ExprImplicits, Proc, Obj, WorkspaceHandle, Bounce}
import de.sciss.synth.ugen.SampleRate
import de.sciss.topology.Topology
import Util._

import scala.annotation.tailrec
import scala.collection.immutable.{IndexedSeq => Vec}
import scala.concurrent.duration.Duration
import scala.concurrent.{TimeoutException, Await, Future}

final class MutagenImpl(val config: Mutagen.Config)
  extends Mutagen with ProcessorImpl[Mutagen.Product, Mutagen.Repr] {

  private val DEBUG = false

  implicit val random = new util.Random(config.seed)

  protected def body(): Vec[Evaluated] = {
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
    val inputSpec         = AudioFile.readSpec(config.in)
    require(inputSpec.numChannels == 1, s"Input file '${config.in.name}' must be mono but has ${inputSpec.numChannels} channels")
    val exCfg             = FeatureExtraction.Config()
    exCfg.audioInput      = config.in
    exCfg.featureOutput   = File.createTemp(suffix = ".aif")
    val inputExtr         = File.createTemp(suffix = "_feat.xml")
    exCfg.metaOutput      = Some(inputExtr)
    val futInputExtr      = FeatureExtraction(exCfg)
    futInputExtr.start()
    futInputExtr.onFailure {
      case t => println(s"futInputExtr failed with $t")
    }

    // -- 2 --
    // generate initial random population
    val pop0  = Vector.fill(config.population)(mkIndividual())

    // make sure that `inputExtr` is ready
    await[Unit](futInputExtr, offset = 0.0, weight = 0.01)

    // -- 3 and 4 --
    // render the chromosome as audio and calculate fitness using Strugatzki
    val evalFut = pop0.map { c =>
      evaluate(c, inputSpec, inputExtr)
    }

    val eval = Await.result[Vec[Evaluated]](Future.sequence(evalFut), Duration.Inf)

    eval.sortBy(_.fitness).reverse
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

  private case class GenExtrFailed(cause: Throwable) extends Exception(cause)

  def evaluate(c: Chromosome, inputSpec: AudioFileSpec, inputExtr: File): Future[Evaluated] = {
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
    val numFrames                   = inputSpec.numFrames
    val duration                    = numFrames.toDouble / inputSpec.sampleRate
    bncCfg.server.nrtOutputPath     = audioF.path
    bncCfg.server.inputBusChannels  = 0
    bncCfg.server.outputBusChannels = 1
    bncCfg.server.sampleRate        = inputSpec.sampleRate.toInt
    // bc.init : (S#Tx, Server) => Unit
    bncCfg.span   = Span(0L, (duration * Timeline.SampleRate).toLong)
    val bnc0  = Bounce[S, S].apply(bncCfg)
    bnc0.start()

    val bnc = Future {
      Await.result(bnc0, Duration(4.0, TimeUnit.SECONDS))
    }
    //    bnc.onFailure {
    //      case t => println(s"bnc failed with $t")
    //    }

    val genFolder           = File.createTemp(directory = true)
    val genExtr             = genFolder / "gen_feat.xml"

    val ex = bnc.flatMap { _ =>
      val exCfg             = FeatureExtraction.Config()
      exCfg.audioInput      = audioF
      exCfg.featureOutput   = File.createTemp(suffix = ".aif")
      exCfg.metaOutput      = Some(genExtr)
      val _ex               = FeatureExtraction(exCfg)
      _ex.start()
      //      _ex.onFailure {
      //        case t => println(s"gen-extr failed with $t")
      //      }
      _ex.recover {
        case cause => GenExtrFailed(cause)
      }
    }

    val corr = ex.flatMap { _ =>
      val corrCfg           = FeatureCorrelation.Config()
      corrCfg.metaInput     = inputExtr
      corrCfg.databaseFolder= genFolder
      corrCfg.minSpacing    = Long.MaxValue >> 1
      corrCfg.numMatches    = 1
      corrCfg.numPerFile    = 1
      corrCfg.maxBoost      = 8f
      corrCfg.normalize     = false   // ok?
      corrCfg.minPunch      = numFrames
      corrCfg.maxPunch      = numFrames
      corrCfg.punchIn       = FeatureCorrelation.Punch(span = Span(0L, numFrames), temporalWeight = 0.5f)
      val _corr             = FeatureCorrelation(corrCfg)
      _corr.start()
      _corr
    }

    val simFut0 = corr.map { matches =>
      // assert(matches.size == 1)
      val sim0 = matches.headOption.map(_.sim).getOrElse(0f)
      val sim  = if (sim0.isNaN || sim0.isInfinite) 0.0 else sim0.toDouble
      sim
    }

    val simFut = simFut0.recover {
      case Bounce.ServerFailed(_) => 0.0
      case GenExtrFailed(_) =>
        if (DEBUG) println("Gen-extr failed!")
        0.0

      case _: TimeoutException    =>
        if (DEBUG) println("Bounce timeout!")
        bnc0.abort()
        0.0    // we aborted the process after 4 seconds
    }

    simFut.map(new Evaluated(c, _))
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
    new Chromosome(t0, seed = random.nextLong())
  }
}
