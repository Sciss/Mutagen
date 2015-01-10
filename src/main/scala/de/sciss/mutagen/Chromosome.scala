/*
 *  Chromosome.scala
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

import de.sciss.synth.ugen.SampleRate
import de.sciss.synth.{UndefinedRate, ugen, GE, SynthGraph, UGenSpec}
import de.sciss.topology.Topology

import scala.annotation.tailrec
import scala.collection.immutable.{IndexedSeq => Vec}

object Vertex {
  object UGen {
    def apply(info: UGenSpec): UGen = new Impl(info)
    def unapply(v: UGen): Option[UGenSpec] = Some(v.info)

    private final class Impl(val info: UGenSpec) extends UGen {
      def instantiate(ins: Vec[(AnyRef, Class[_])]): GE = {
        val consName = info.rates.method match {
          case UGenSpec.RateMethod.Alias (name) => name
          case UGenSpec.RateMethod.Custom(name) => name
          case UGenSpec.RateMethod.Default =>
            val rate = info.rates.set.max
            rate.methodName
        }

        val (consValues, consTypes) = ins.unzip

        // yes I know, we could use Scala reflection
        val companionName   = s"de.sciss.synth.ugen.${info.name}$$"
        val companionClass  = Class.forName(companionName)
        val companionMod    = companionClass.getField("MODULE$").get(null)
        val cons            = companionClass.getMethod(consName, consTypes: _*)
        val ge              = cons.invoke(companionMod, consValues: _*).asInstanceOf[GE]
        ge
      }
    }
  }
  trait UGen extends Vertex {
    def info: UGenSpec

    override def toString = s"${info.name}@${hashCode().toHexString}"

    def instantiate(ins: Vec[(AnyRef, Class[_])]): GE
  }
  //  class UGen(val info: UGenSpec) extends Vertex {
  //    override def toString = s"${info.name}@${hashCode().toHexString}"
  //  }
  object Constant {
    def apply(f: Float) = new Constant(f)
    def unapply(v: Constant): Option[Float] = Some(v.f)
  }
  class Constant(val f: Float) extends Vertex {
    override def toString = s"$f@${hashCode().toHexString}"
  }
}
sealed trait Vertex

/** The edge points from '''consuming''' (source) element to '''input''' element (target).
  * Therefore, the `sourceVertex`'s `inlet` will be occupied by `targetVertex`
  */
case class Edge(sourceVertex: Vertex, targetVertex: Vertex, inlet: String) extends Topology.Edge[Vertex]

class Chromosome(val top: Topology[Vertex, Edge], val seed: Long) {
  lazy val graph: SynthGraph = {
    import Util._
    implicit val rnd = new util.Random(seed)
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
}

class Evaluated(val chromosome: Chromosome, val fitness: Double) {
  def graph: SynthGraph = chromosome.graph

  override def toString = f"[${graph.sources.size} sources; fitness = $fitness%1.2f]@${hashCode.toHexString}"
}