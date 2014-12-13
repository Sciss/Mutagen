package de.sciss.mutagen
package impl

import de.sciss.synth.UGenSpec
import de.sciss.topology.Topology

object Vertex {
  object UGen {
    def apply(info: UGenSpec) = new UGen(info)
    def unapply(v: UGen): Option[UGenSpec] = Some(v.info)
  }
  class UGen(val info: UGenSpec) extends Vertex {
    override def toString = s"${info.name}@${hashCode().toHexString}"
  }
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

class Chromosome(val top: Topology[Vertex, Edge]) {
  // override def toString = top.toString()
}
