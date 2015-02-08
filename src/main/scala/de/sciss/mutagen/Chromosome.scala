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
import de.sciss.synth.ugen.BinaryOpUGen
import de.sciss.synth.{GE, SynthGraph, UGenSpec}
import de.sciss.topology.Topology

import scala.concurrent.{ExecutionContext, Future}
import scala.util.hashing.MurmurHash3

object Vertex {
  object UGen {
    def apply(info: UGenSpec): UGen = new Impl(info)
    def unapply(v: UGen): Option[UGenSpec] = Some(v.info)

    private final class Impl(val info: UGenSpec) extends UGen {
      private def isBinOp: Boolean = info.name.startsWith("Bin_")

      def copy(): UGen = new Impl(info)

      def instantiate(ins: Vec[(AnyRef, Class[_])]): GE =
        if (isBinOp) mkBinOpUGen(ins) else mkRegularUGen(ins)

      def asCompileString(ins: Vec[String]): String =
        if (isBinOp) mkBinOpString(ins) else mkRegularString(ins)

      def boxName =
        if (isBinOp) {
          val id = info.name.substring(4).toInt
          val op = BinaryOpUGen.Op(id)
          val n   = op.name
          s"${n.substring(0, 1).toLowerCase}${n.substring(1)}"
        } else {
          info.name
        }

      private def mkBinOpString(ins: Vec[String]): String = {
        val nu = boxName
        s"(${ins(0)} $nu ${ins(1)})"
      }

      private def mkRegularString(ins: Vec[String]): String = {
        val rates = info.rates
        val consName0 = rates.method match {
          case UGenSpec.RateMethod.Alias (name) => name
          case UGenSpec.RateMethod.Custom(name) => name
          case UGenSpec.RateMethod.Default =>
            val rate = rates.set.max
            rate.methodName
        }
        val consName  = if (consName0 == "apply") "" else s".$consName0"
        val nameCons  = s"${info.name}$consName"
        if (ins.isEmpty && consName.nonEmpty)   // e.g. SampleRate.ir
          nameCons
        else
          ins.mkString(s"$nameCons(", ", ", ")")
      }

      private def mkBinOpUGen(ins: Vec[(AnyRef, Class[_])]): GE = {
        val id = info.name.substring(4).toInt
        val op = BinaryOpUGen.Op(id)
        op.make(ins(0)._1.asInstanceOf[GE], ins(1)._1.asInstanceOf[GE])
      }

      private def mkRegularUGen(ins: Vec[(AnyRef, Class[_])]): GE = {
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

    def asCompileString(ins: Vec[String]): String
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
    def copy(): Constant = new Constant(f)

    def boxName = f.toString
  }
}
sealed trait Vertex {
  /** Creates an structurally identical copy, but wrapped in a new vertex (object identity). */
  def copy(): Vertex

  def boxName: String
}

/** The edge points from '''consuming''' (source) element to '''input''' element (target).
  * Therefore, the `sourceVertex`'s `inlet` will be occupied by `targetVertex`
  */
case class Edge(sourceVertex: Vertex, targetVertex: Vertex, inlet: String) extends Topology.Edge[Vertex]

object Chromosome {
  def apply()(implicit random: util.Random, global: Global): Chromosome = impl.ChromosomeImpl.mkIndividual()
}
class Chromosome(val top: Top, val seed: Long) extends Product {
  lazy val graph: SynthGraph = impl.ChromosomeImpl.mkSynthGraph(this, mono = false, removeNaNs = true)

  def evaluate(eval: Evaluation, inputSpec: AudioFileSpec, inputExtr: File)
              (implicit exec: ExecutionContext, global: Global): Future[Evaluated] =
    impl.ChromosomeImpl.evaluate(this, eval, inputSpec, inputExtr)

  private lazy val numVertices  = top.vertices.size
  private lazy val numEdges     = top.edges   .size

  override def toString = f"[$numVertices vertices; $numEdges edges]@${hashCode().toHexString}"

  def graphAsString: String = impl.ChromosomeImpl.graphAsString(this)

  private lazy val _hashCode: Int = MurmurHash3.productHash(this)

  override def hashCode(): Int = _hashCode

  override def equals(obj: Any): Boolean = obj match {
    case that: Chromosome => (this.graph == that.graph) && (this.seed == that.seed)

    case other => super.equals(obj)
  }

  def productElement(n: Int): Any = n match {
    case 0 => top
    case 1 => seed
  }

  def productArity: Int = 2

  def canEqual(that: Any): Boolean = that.isInstanceOf[Chromosome]
}

class Evaluated(val chromosome: Chromosome, val fitness: Double) {
  def graph: SynthGraph = chromosome.graph

  override def toString = f"[${graph.sources.size} sources; fitness = $fitness%1.2f]@${hashCode.toHexString}"
}