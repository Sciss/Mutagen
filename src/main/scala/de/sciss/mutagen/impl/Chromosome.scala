package de.sciss.mutagen
package impl

import de.sciss.synth.UGenSpec
import de.sciss.topology.Topology

object Vertex {
  case class UGen(info: UGenSpec) extends Vertex
  case class Constant(f: Float)   extends Vertex
}
sealed trait Vertex

case class Edge(sourceVertex: Vertex, targetVertex: Vertex, inlet: String) extends Topology.Edge[Vertex]

class Chromosome(val top: Topology[Vertex, Edge]) {

}
