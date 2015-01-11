/*
 *  Chromosome.scala
 *  (Mutagen)
 *
 *  Copyright (c) 2014-2015 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.mutagen

import de.sciss.file.File
import de.sciss.synth.io.AudioFileSpec
import de.sciss.synth.{GE, SynthGraph, UGenSpec}
import de.sciss.topology.Topology

import scala.collection.immutable.{IndexedSeq => Vec}
import scala.concurrent.{ExecutionContext, Future}

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

object Chromosome {
  def apply()(implicit random: util.Random): Chromosome = impl.ChromosomeImpl.mkIndividual()
}
class Chromosome(val top: Topology[Vertex, Edge], val seed: Long) {
  lazy val graph: SynthGraph = impl.ChromosomeImpl.mkSynthGraph(this)

  def evaluate(inputSpec: AudioFileSpec, inputExtr: File)(implicit exec: ExecutionContext): Future[Evaluated] =
    impl.ChromosomeImpl.evaluate(this, inputSpec, inputExtr)
}

class Evaluated(val chromosome: Chromosome, val fitness: Double) {
  def graph: SynthGraph = chromosome.graph

  override def toString = f"[${graph.sources.size} sources; fitness = $fitness%1.2f]@${hashCode.toHexString}"
}