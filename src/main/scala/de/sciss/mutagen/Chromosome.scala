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
import de.sciss.synth.{GE, SynthGraph, UGenSpec, UndefinedRate, ugen}
import de.sciss.topology.Topology

import scala.annotation.tailrec
import scala.collection.immutable.{IndexedSeq => Vec}

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

class Chromosome(val top: Topology[Vertex, Edge], val graph: SynthGraph)