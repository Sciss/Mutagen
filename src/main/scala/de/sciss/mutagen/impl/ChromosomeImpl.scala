/*
 *  ChromosomeImpl.scala
 *  (Mutagen)
 *
 *  Copyright (c) 2014-2016 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
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
import de.sciss.mutagen.Util._
import de.sciss.numbers
import de.sciss.processor.Processor
import de.sciss.span.Span
import de.sciss.strugatzki.{FeatureCorrelation, FeatureExtraction, Strugatzki}
import de.sciss.synth.io.{AudioFile, AudioFileSpec}
import de.sciss.synth.proc.{Bounce, Proc, TimeRef, WorkspaceHandle}
import de.sciss.synth.ugen.{BinaryOpUGen, SampleRate}
import de.sciss.synth.{GE, SynthGraph, UGenSource, UGenSpec, UndefinedRate, audio, demand, ugen}
import de.sciss.topology.Topology
import play.api.libs.json
import play.api.libs.json.{JsArray, JsError, JsNumber, JsObject, JsResult, JsString, JsSuccess, JsValue}

import scala.annotation.tailrec
import scala.collection.immutable.{IndexedSeq => Vec}
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future, TimeoutException, blocking}
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
    "RandID", "RandSeed",
    "Rand", "ExpRand", "IRand",
    /* "A2K", */ "K2A" /* , "DC" */
  )

  // these have done-action side-effects but we require doNothing, so they are allowed
  private val AddUGens = Set[String]("DetectSilence", "LFGauss", "Line", "Linen", "XLine")

  private val ugens0: Vec[UGenSpec] = (UGenSpec.standardUGens.valuesIterator.filter { spec =>
    spec.attr.intersect(NoNoAttr).isEmpty && !RemoveUGens.contains(spec.name) && spec.outputs.nonEmpty &&
      !spec.rates.set.contains(demand)
  } ++ UGenSpec.standardUGens.valuesIterator.filter { spec => AddUGens.contains(spec.name) }).toIndexedSeq

  private val binUGens: Vec[UGenSpec] = {
    import BinaryOpUGen._
    val ops = Vector(Plus, Minus, Times, Div, Mod, Eq, Neq, Lt, Gt, Leq, Geq, Min, Max, BitAnd, BitOr, BitXor,
      RoundTo, RoundUpTo, Trunc, Atan2, Hypot, Hypotx, Pow, Ring1, Ring2, Ring3, Ring4, Difsqr, Sumsqr, Sqrsum,
      Sqrdif, Absdif, Thresh, Amclip, Scaleneg, Clip2, Excess, Fold2, Wrap2
    )
    ops.map { op =>
      val name  = s"Bin_${op.id}"
      val rates = UGenSpec.Rates.Set(Set(audio))
      val arg1  = UGenSpec.Argument(name = "a", tpe = UGenSpec.ArgumentType.GE(UGenSpec.SignalShape.Generic),
        defaults = Map.empty, rates = Map.empty)
      val arg2  = arg1.copy(name = "b")
      val in1   = UGenSpec.Input(arg = "a", tpe = UGenSpec.Input.Single)
      val in2   = in1.copy(arg = "b")
      val out   = UGenSpec.Output(name = None, shape = UGenSpec.SignalShape.Generic, variadic = None)
      UGenSpec.apply(name = name, attr = Set.empty, rates = rates, args = Vec(arg1, arg2),
        inputs = Vec(in1, in2), outputs = Vec(out), doc = None)
    }
  }

  private val ugens: Vec[UGenSpec] = ugens0 ++ binUGens

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

  def geArgs(spec: UGenSpec): Vec[UGenSpec.Argument] = {
    val res       = spec.args.filter { arg =>
      arg.tpe match {
        case UGenSpec.ArgumentType.Int => false
        case UGenSpec.ArgumentType.GE(UGenSpec.SignalShape.DoneAction, _) => false
        case _ => true
      }
    }
    res
  }

  def findIncompleteUGenInputs(t1: Top, v: Vertex.UGen): Vec[String] = {
    val spec      = v.info
    val edgeSet   = t1.edgeMap.getOrElse(v, Set.empty)
    val argsFree  = geArgs(spec).filter { arg => !edgeSet.exists(_.inlet == arg.name) }
    val inc       = argsFree.filterNot(_.defaults.contains(UndefinedRate))
    inc.map(_.name)
  }

  def completeUGenInputs(t1: Top, v: Vertex.UGen)(implicit random: Random, global: Global): Top = {
    import global.nonDefaultProb
    val spec    = v.info
    // An edge's source is the consuming UGen, i.e. the one whose inlet is occupied!
    // A topology's edgeMap uses source-vertices as keys. Therefore, we can see
    // if the an argument is connected by getting the edges for the ugen and finding
    // an edge that uses the inlet name.
    val edgeSet = t1.edgeMap.getOrElse(v, Set.empty)
    val argsFree = geArgs(spec).filter { arg => !edgeSet.exists(_.inlet == arg.name) }
    val (hasDef, hasNoDef)          = argsFree.partition(_.defaults.contains(UndefinedRate))
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

  def mkUGen()(implicit random: Random): Vertex.UGen =  {
    val spec    = choose(ugens)
    val v       = Vertex.UGen(spec)
    v
  }

  def addVertex(pred: Top)(implicit random: Random, global: Global): Top = {
    import global.constProb
    val next: Top = if (coin(constProb)) {
      val v = mkConstant()
      pred.addVertex(v)

    } else {
      val v   = mkUGen()
      val t1  = pred.addVertex(v)
      completeUGenInputs(t1, v)
    }
    next
  }

  def mkIndividual()(implicit random: Random, global: Global): Chromosome = {
    import global.{maxNumVertices, minNumVertices}
    val num = rrand(minNumVertices, maxNumVertices)

    @tailrec def loopGraph(pred: Top): Top =
      if (pred.vertices.size >= num) pred else loopGraph(addVertex(pred))

    val t0 = loopGraph(Topology.empty)
    new Chromosome(t0, seed = random.nextLong())
  }

  def mkSynthGraph(c: Chromosome, mono: Boolean, removeNaNs: Boolean): SynthGraph = {
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
                    val xOpt = arg.defaults.get(UndefinedRate)
                    val x    = xOpt.getOrElse {
                      val inc = findIncompleteUGenInputs(top, u)
                      println("INCOMPLETE:")
                      inc.foreach(println)
                      sys.error(s"Vertex $spec has no input for inlet $arg")
                    }
                    x match {
                      case UGenSpec.ArgumentValue.Boolean(v)    => ugen.Constant(if (v) 1 else 0)
                      case UGenSpec.ArgumentValue.DoneAction(v) => ugen.Constant(v.id)
                      case UGenSpec.ArgumentValue.Float(v)      => ugen.Constant(v)
                      case UGenSpec.ArgumentValue.Inf           => ugen.Constant(Float.PositiveInfinity)
                      case UGenSpec.ArgumentValue.Int(v)        => ugen.Constant(v)
                      case UGenSpec.ArgumentValue.Nyquist       => SampleRate.ir / 2
                      case UGenSpec.ArgumentValue.String(v)     => UGenSource.stringArg(v)
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
      import de.sciss.synth.ugen._
      RandSeed.ir()
      val map   = loop(top.vertices, Map.empty)
      val ugens = top.vertices.collect {
        case ugen: Vertex.UGen => ugen
      }
      if (ugens.nonEmpty) {
        val roots = getRoots(top)
        val sig0: GE = if (roots.isEmpty) map(choose(ugens)) else Mix(roots.map(map.apply))
        val sig1  = /* if (mono) */ Mix.mono(sig0) /* else sig0 */
        val sig2  = if (!removeNaNs) sig1 else {
            val isOk = CheckBadValues.ar(sig1, post = 0) sig_== 0
            Gate.ar(sig1, isOk)
          }
        val sig3  = Limiter.ar(LeakDC.ar(sig2))
        val sig   = if (mono) sig3 else Pan2.ar(sig3) // SplayAz.ar(numChannels = 2, in = sig3)
        Out.ar(0, sig)
      }
    }
  }

  private def getRoots(top: Top): Vec[Vertex.UGen] = {
    val ugens = top.vertices.collect {
      case ugen: Vertex.UGen => ugen
    }
    ugens.filter { ugen =>
      top.edges.forall(_.targetVertex != ugen)
    }
  }

  private val featNorms = Array[Array[Float]](
    Array(0.006015186f,1.4569731f),
    Array(-1.4816481f,3.093808f),
    Array(-1.4089416f,1.267046f),
    Array(-0.860692f,1.4034394f),
    Array(-0.65952975f,1.431201f),
    Array(-0.66072506f,0.8506244f),
    Array(-0.2808966f,0.90672106f),
    Array(-0.29912513f,0.705802f),
    Array(-0.22443223f,0.67802113f),
    Array(-0.1471797f,0.68207365f),
    Array(-0.104354106f,0.6723507f),
    Array(-0.2412649f,0.70821077f),
    Array(-0.16983563f,0.6771785f),
    Array(-0.10048226f,0.64655834f)
  )

  def bounce(c: Chromosome, audioF: File, inputSpec: AudioFileSpec, inputExtr: File, duration0: Double = -1)
            (implicit exec: ExecutionContext): Processor[Any] = {
    type S  = InMemory
    implicit val cursor = InMemory()  // XXX TODO - create that once
//    val exp = ExprImplicits[S]

    val objH = cursor.step { implicit tx =>
      val proc      = Proc[S]
      proc.graph()  = mkSynthGraph(c, mono = true, removeNaNs = false) // c.graph
    val procObj   = proc // Obj(Proc.Elem(proc))
      tx.newHandle(procObj)
    }
    import WorkspaceHandle.Implicits._
    val bncCfg              = Bounce.Config[S]
    bncCfg.group            = objH :: Nil
    // val audioF           = File.createTemp(prefix = "muta_bnc", suffix = ".aif")
    val duration            = if (duration0 > 0) duration0 else inputSpec.numFrames.toDouble / inputSpec.sampleRate
    val sCfg                = bncCfg.server
    sCfg.nrtOutputPath      = audioF.path
    sCfg.inputBusChannels   = 0
    sCfg.outputBusChannels  = 1
    sCfg.wireBuffers        = 1024 // higher than default
    sCfg.blockSize          = 64   // keep it compatible to real-time
    sCfg.sampleRate         = inputSpec.sampleRate.toInt
    // bc.init : (S#Tx, Server) => Unit
    bncCfg.span             = Span(0L, (duration * TimeRef.SampleRate).toLong)
    val bnc0                = Bounce[S, S].apply(bncCfg)
    bnc0.start()
    bnc0
  }

  def evaluate(c: Chromosome, eval: Evaluation, inputSpec: AudioFileSpec, inputExtr: File)
              (implicit exec: ExecutionContext, global: Global): Future[Evaluated] = {
    val audioF  = File.createTemp(prefix = "muta_bnc", suffix = ".aif")
    val bnc0    = bounce(c, audioF = audioF, inputSpec = inputSpec, inputExtr = inputExtr)

    val bnc = Future {
      Await.result(bnc0, Duration(4.0, TimeUnit.SECONDS))
      // XXX TODO -- would be faster if we could use a Poll during
      // the bounce and instruct the bounce proc to immediately terminate
      // when seeing a particular message in the console?
      blocking {
        val af = AudioFile.openRead(audioF)
        try {
          val bufSize = 512
          val b       = af.buffer(bufSize)
          var i       = 0L
          while (i < af.numFrames) {
            val len = math.min(bufSize, af.numFrames - i).toInt
            af.read(b, 0, len)
            var ch = 0
            while (ch < af.numChannels) {
              val bc = b(ch)
              var j = 0
              while (j < len) {
                if (bc(j).isNaN || bc(j).isInfinite) {
                  if (DEBUG) println("Detected NaNs")
                  throw FeatureExtractionFailed(null)
                }
                j += 1
              }
              ch += 1
            }
            i += len
          }
        } finally {
          af.cleanUp()
        }
      }
    }
    //    bnc.onFailure {
    //      case t => println(s"bnc failed with $t")
    //    }

    val genFolder           = File.createTemp(prefix = "muta_eval", directory = true)
    val genExtr             = genFolder / "gen_feat.xml"

    val normF   = genFolder / Strugatzki.NormalizeName
    if (eval.normalize) {
      if (eval.numCoeffs != featNorms.length + 1)
        throw new IllegalArgumentException(s"Normalize option requires numCoeffs == ${featNorms.length - 1}")
      blocking {
        val normAF  = AudioFile.openWrite(normF, AudioFileSpec(numChannels = featNorms.length, sampleRate = 44100))
        normAF.write(featNorms)
        normAF.close()
      }
    }
    val featF   = File.createTemp(prefix = "gen_feat", suffix = ".aif")

    val ex = bnc.flatMap { _ =>
      val exCfg             = FeatureExtraction.Config()
      exCfg.audioInput      = audioF
      exCfg.featureOutput   = featF
      exCfg.metaOutput      = Some(genExtr)
      exCfg.numCoeffs       = eval.numCoeffs
      val _ex               = FeatureExtraction(exCfg)
      _ex.start()
      //      _ex.onFailure {
      //        case t => println(s"gen-extr failed with $t")
      //      }
      _ex.recover {
        case cause => throw FeatureExtractionFailed(cause)
      }
    }

    val numFrames = inputSpec.numFrames

    val corr = ex.flatMap { _ =>
      val corrCfg           = FeatureCorrelation.Config()
      corrCfg.metaInput     = inputExtr
      corrCfg.databaseFolder= genFolder
      corrCfg.minSpacing    = Long.MaxValue >> 1
      corrCfg.numMatches    = 1
      corrCfg.numPerFile    = 1
      corrCfg.maxBoost      = eval.maxBoost.toFloat
      corrCfg.normalize     = eval.normalize
      corrCfg.minPunch      = numFrames
      corrCfg.maxPunch      = numFrames
      corrCfg.punchIn       = FeatureCorrelation.Punch(
        span = Span(0L, numFrames),
        temporalWeight = eval.temporalWeight.toFloat)
      val _corr             = FeatureCorrelation(corrCfg)
      _corr.start()
      _corr
    }

    val simFut0 = corr.map { matches =>
      // assert(matches.size == 1)
      val sim0 = matches.headOption.map { m =>
        if (DEBUG) println(m)
        m.sim
      } .getOrElse(0f)
      val sim  = if (sim0.isNaN || sim0.isInfinite) 0.0 else sim0.toDouble
      sim
    }

    val simFut = simFut0.recover {
      case Bounce.ServerFailed(_) => 0.0
      case FeatureExtractionFailed(_) =>
        if (DEBUG) println("Gen-extr failed!")
        0.0

      case _: TimeoutException =>
        if (DEBUG) println("Bounce timeout!")
        bnc0.abort()
        0.0    // we aborted the process after 4 seconds
    }

    val res = simFut.map { sim0 =>
      import numbers.Implicits._
      val pen = eval.vertexPenalty
      val sim = if (pen <= 0) sim0 else
        sim0 - c.top.vertices.size.linlin(global.minNumVertices, global.maxNumVertices, 0, pen)
      new Evaluated(c, sim)
    }

    res.onComplete { _ =>
      if (eval.normalize) normF.delete()
      featF.delete()
      audioF.delete()
      genExtr.delete()
      genFolder.delete()
    }
    res
  }

  private case class StringRep(lhs: String, rhs: String) {
    override def toString = s"val $lhs = $rhs"
  }

  def graphAsString(c: Chromosome): String = {
    import Util._
    import c.{seed, top}
    implicit val rnd = new Random(seed)
    val vertices    = top.vertices
    val numVertices = vertices.size

    @tailrec def loop(rem: Vec[Vertex], real: Map[Vertex, StringRep]): Map[Vertex, StringRep] = rem match {
      case init :+ last =>
        val value: String = last match {
          case Vertex.Constant(f) => f.toString // XXX TODO
          case u @ Vertex.UGen(spec) =>
            val ins = spec.args.map { arg =>
              val res: String /* (AnyRef, Class[_]) */ = arg.tpe match {
                case UGenSpec.ArgumentType.Int =>
                  val v = arg.defaults.get(UndefinedRate) match {
                    case Some(UGenSpec.ArgumentValue.Int(i)) => i
                    case _ => rrand(1, 2)
                  }
                  v.toString

                case UGenSpec.ArgumentType.GE(_, _) =>
                  val inGEOpt = top.edgeMap.getOrElse(last, Set.empty).flatMap { e =>
                    if (e.inlet == arg.name) real.get(e.targetVertex).map(_.lhs) else None
                  } .headOption
                  val inGE = inGEOpt.getOrElse {
                    val xOpt = arg.defaults.get(UndefinedRate)
                    val x    = xOpt.getOrElse {
                      val inc = findIncompleteUGenInputs(top, u)
                      println("INCOMPLETE:")
                      inc.foreach(println)
                      sys.error(s"Vertex $spec has no input for inlet $arg")
                    }
                    x match {
                      case UGenSpec.ArgumentValue.Boolean(v)    => (if (v) 1 else 0).toString
                      case UGenSpec.ArgumentValue.DoneAction(v) => v.name
                      case UGenSpec.ArgumentValue.Float(v)      => v.toString
                      case UGenSpec.ArgumentValue.Inf           => "inf"
                      case UGenSpec.ArgumentValue.Int(v)        => v.toString
                      case UGenSpec.ArgumentValue.Nyquist       => "SampleRate.ir/2"
                      case UGenSpec.ArgumentValue.String(v)     => v
                    }
                  }
                  inGE
              }
              res
            }

            val name = u.asCompileString(ins)
            if (name.charAt(0) == '(' && name.charAt(name.length - 1) == ')') name.substring(1, name.length - 1) else name
        }

        val valName = s"v${numVertices - rem.size}"
        val rep     = StringRep(valName, value)
        loop(init, real + (last -> rep))

      case _ =>  real
    }

    //    val ugens = top.vertices.collect {
    //      case ugen: Vertex.UGen => ugen
    //    }
    val roots     = getRoots(top)
    val map       = loop(vertices, Map.empty)
    val rootsSym  = roots.map(map(_).lhs)
    val verticesS = vertices.map(map(_).toString).reverse.mkString("\n")
    val mixS      = rootsSym.toList match {
      case Nil => ""
      case nonEmpty =>
        val mixS0 = nonEmpty match {
          case single :: Nil => s"val sig = $single"
          case multiple =>
            val rootsS = multiple.mkString("val roots = Vector(", ", ", ")")
            s"$rootsS\nval sig = Mix(roots)"

        }
        s"$mixS0\nLimiter.ar(LeakDC.ar(sig))\n"
    }
    s"RandSeed.ir()\n$verticesS\n$mixS"
  }
}
