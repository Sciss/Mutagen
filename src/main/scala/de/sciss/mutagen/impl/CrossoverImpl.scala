/*
 *  CrossoverImpl.scala
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
package impl

import de.sciss.kollflitz
import de.sciss.muta.BreedingFunction
import de.sciss.topology.Topology

import scala.annotation.tailrec
import scala.util.Random

object CrossoverImpl extends BreedingFunction[Chromosome, Global] {
  val DEBUG = false

  def apply(genome: Vec[Chromosome], sz: Int, glob: Global, rnd: Random): Vec[Chromosome] = {
    import kollflitz.RandomOps._
    implicit val random = rnd
    implicit val global = glob
    val gs = genome.scramble()

    // - choose two parents
    // - choose relative cutting point; determine absolute point in both parents
    // - create two child chromosomes from split parents
    // - fix them by completing missing links
    // - for now: ignore min/max vertices
    val res     = (0 until ((sz + 1) & ~1)).flatMap { i =>
      val pIdx1   = rnd.nextInt(genome.size)
      val pIdx2a  = rnd.nextInt(genome.size - 1)
      val pIdx2   = if (pIdx2a < pIdx1) pIdx2a else pIdx2a + 1  // so pIdx1 != pIdx2
      val posRel  = rnd.nextFloat()
      val p1      = genome(pIdx1)
      val p2      = genome(pIdx2)

      val top1    = p1.top
      val top2    = p2.top
      val v1      = top1.vertices
      val v2      = top2.vertices
      val pos1    = (posRel * v1.size - 1).toInt + 1
      val pos2    = (posRel * v2.size - 1).toInt + 1

      val (head1, tail1)  = v1.splitAt(pos1)
      val (head2, tail2)  = v2.splitAt(pos2)
      val edgesHead1      = top1.edges.filter(e => head1.contains(e.sourceVertex) && head1.contains(e.targetVertex))
      val edgesTail1      = top1.edges.filter(e => tail1.contains(e.sourceVertex) && tail1.contains(e.targetVertex))
      val edgesHead2      = top2.edges.filter(e => head2.contains(e.sourceVertex) && head2.contains(e.targetVertex))
      val edgesTail2      = top2.edges.filter(e => tail2.contains(e.sourceVertex) && tail2.contains(e.targetVertex))

      @tailrec def shrinkTop(top: Top, target: Int, iter: Int): Top =
        if (top.vertices.size <= target || iter == global.maxNumVertices) top else {
          val (top1, _) = MutationImpl.removeVertex1(top)
          shrinkTop(top1, target = target, iter = iter + 1)
        }

      def mkTop(vertices: Vec[Vertex], edges: Set[Edge]): Top = {
        val t0 = Topology.empty[Vertex, Edge]
        val (t1, e1) = ((t0, edges) /: vertices) { case ((t2, e2), v0) =>
          // two parents might share the same vertices from a common
          // ancestry; in that case we must individualize the vertex
          // (making a copy means they get fresh object identity and hash)
          val isNew = !t2.vertices.contains(v0)
          val v     = if (isNew) v0 else v0.copy()
          val t3    = t2.addVertex(v)
          val e3    = if (isNew) e2 else e2.map { e =>
            if      (e.sourceVertex == v0) e.copy(sourceVertex = v)
            else if (e.targetVertex == v0) e.copy(targetVertex = v)
            else e
          }
          (t3, e3)
        }
        (t1 /: e1)(_.addEdge(_).get._1)
      }

      val topC1a = mkTop(head1 ++ tail2, edgesHead1 ++ edgesTail2)
      val topC2a = mkTop(head2 ++ tail1, edgesHead2 ++ edgesTail1)

      def complete(top: Top): Top = {
        val inc = top.vertices.collect {
          case v: Vertex.UGen if ChromosomeImpl.findIncompleteUGenInputs(top, v).nonEmpty => v
        }
        val top1 = if (inc.isEmpty) top else (top /: inc)((res, v) => ChromosomeImpl.completeUGenInputs(res, v))
        val top2 = shrinkTop(top1, top.vertices.size, 0)
        top2
      }

      val topC1 = complete(topC1a)
      val topC2 = complete(topC2a)

      val c1  = new Chromosome(topC1, rnd.nextLong())
      val c2  = new Chromosome(topC2, rnd.nextLong())

      if (DEBUG) {
        val s1 = s"p1 = (${v1.size}, ${top1.edges.size}), p2 = (${v2.size}, ${top2.edges.size})"
        val s2 = s"c1 = (${topC1.vertices.size}, ${topC1.edges.size}), c2 = (${topC2.vertices.size}, ${topC2.edges.size})"
        println(s"crossover. $s1. $s2")
      }

      Vector(c1, c2)
    }

    if (res.size == sz) res else res.take(sz)
  }
}