package de.sciss.mutagen
package impl

import java.util.concurrent.TimeUnit

import de.sciss.file._
import de.sciss.lucre.synth.InMemory
import de.sciss.mutagen.Util._
import de.sciss.span.Span
import de.sciss.strugatzki.{FeatureCorrelation, FeatureExtraction}
import de.sciss.synth.io.AudioFileSpec
import de.sciss.synth.proc.{Timeline, Bounce, WorkspaceHandle, Obj, Proc, ExprImplicits}
import de.sciss.synth.ugen.SampleRate
import de.sciss.synth.{SynthGraph, ugen, GE, demand, UndefinedRate, UGenSpec}
import de.sciss.topology.Topology
import play.api.libs.json
import play.api.libs.json.{JsArray, JsObject, JsError, JsSuccess, JsString, JsNumber, JsResult, JsValue}

import scala.annotation.tailrec
import scala.collection.immutable.{IndexedSeq => Vec}
import scala.concurrent.{ExecutionContext, TimeoutException, Await, Future}
import scala.concurrent.duration.Duration
import scala.util.Random

object ChromosomeImpl {
  private val DEBUG = false
  private type Top = Topology[Vertex, Edge]

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

  private implicit object VertexFormat extends json.Format[Vertex] {
    def reads(json: JsValue): JsResult[Vertex] = {
      json match {
        case JsNumber(v) => JsSuccess(Vertex.Constant(v.toFloat))
        case JsString(s) => ugens.find(_.name == s)
          .fold[JsResult[Vertex]](JsError(s"UGen '$s' not defined"))(info => JsSuccess(Vertex.UGen(info)))
        case _ => JsError("Expected number or string")
      }
    }

    def writes(v: Vertex): JsValue = {
      v match {
        case Vertex.Constant(f) => JsNumber(f.toDouble)
        case Vertex.UGen(info)  => JsString(info.name)
      }
    }
  }

  // private implicit val EdgeFormat = AutoFormat[Edge]

  object Format extends json.Format[Chromosome] {
    def reads(json: JsValue): JsResult[Chromosome] = json match {
      case JsObject(Seq(("vertices", JsArray(jsVertices)), ("edges", JsArray(jsEdges)), ("seed", JsNumber(jsSeed)))) =>
        val vertices0 = ((JsSuccess(Vector.empty[Vertex]): JsResult[Vec[Vertex]]) /: jsVertices) {
          case (JsSuccess(res, _), js) =>
            val v0 = VertexFormat.reads(js)
            v0 match {
              case JsSuccess(v, _)  => JsSuccess(res :+ v)
              case JsError(x)       => JsError(x)
            }
          case (err, _) => err
        }
        vertices0.flatMap { vertices =>
          val edges0 = ((JsSuccess(Vector.empty[Edge]): JsResult[Vec[Edge]]) /: jsEdges) {
            case (JsSuccess(res, _), js) =>
              js match {
                case JsObject(Seq(("source", JsNumber(jsSource)), ("target", JsNumber(jsTarget)), ("inlet", JsString(jsInlet)))) =>
                  val source  = vertices(jsSource.toInt)
                  val target  = vertices(jsTarget.toInt)
                  val edge    = Edge(source, target, jsInlet)
                  JsSuccess(res :+ edge)
                case _ => JsError("Malformed JSON - not an object with 'source', 'target', 'inlet'")
              }

            case (err, _) => err
          }
          edges0.map { edges =>
            val top0 = Topology.empty[Vertex, Edge]
            val top1 = (top0 /: vertices)(_ addVertex _)
            val top2 = (top1 /: edges   )(_.addEdge(_).get._1)
            new Chromosome(top2, seed = jsSeed.toLong)
          }
        }

      case _ => JsError("Malformed JSON - not an object with arrays 'vertices' and 'edges'")
    }

    def writes(c: Chromosome): JsValue = {
      val vertices    = c.top.vertices
      val edges       = c.top.edges
      val jsVertices  = JsArray(vertices   .map(VertexFormat.writes))
      val jsEdges     = JsArray(edges.toSeq.map { edge =>
        val source  = vertices.indexOf(edge.sourceVertex)
        val target  = vertices.indexOf(edge.targetVertex)
        JsObject(Seq("source" -> JsNumber(source), "target" -> JsNumber(target), "inlet" -> JsString(edge.inlet)))
      })
      JsObject(Seq("vertices" -> jsVertices, "edges" -> jsEdges, "seed" -> JsNumber(c.seed)))
    }
  }

  def mkConstant()(implicit random: Random): Vertex.Constant = {
    val f0  = exprand(0.001, 10000.001) - 0.001
    val f   = if (coin(0.25)) -f0 else f0
    val v   = Vertex.Constant(f.toFloat)
    v
  }

  def mkIndividual()(implicit random: Random): Chromosome = {
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

  def mkSynthGraph(c: Chromosome): SynthGraph = {
    import Util._
    import c.{seed, top}
    implicit val rnd = new Random(seed)
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
        val isOk  = CheckBadValues.ar(sig0, post = 0) sig_== 0
        val sig1  = Gate.ar(sig0, isOk)
        val sig   = Limiter.ar(LeakDC.ar(sig1))
        Out.ar(0, sig)
      }
    }
  }

  def evaluate(c: Chromosome, inputSpec: AudioFileSpec, inputExtr: File)
              (implicit exec: ExecutionContext): Future[Evaluated] = {
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
        case cause => Mutagen.FeatureExtractionFailed(cause)
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
      case Mutagen.FeatureExtractionFailed(_) =>
        if (DEBUG) println("Gen-extr failed!")
        0.0

      case _: TimeoutException =>
        if (DEBUG) println("Bounce timeout!")
        bnc0.abort()
        0.0    // we aborted the process after 4 seconds
    }

    simFut.map(new Evaluated(c, _))
  }
}
