/*
 *  CrossoverImpl.scala
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

import de.sciss.kollflitz
import de.sciss.muta.BreedingFunction
import de.sciss.topology.Topology
import Util.coin

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
    val szH     = (sz + 1) / 2
    val res     = (0 until szH).flatMap { i =>
      // val pIdx1   = rnd.nextInt(genome.size)
      // val pIdx2a  = rnd.nextInt(genome.size - 1)
      // val pIdx2   = if (pIdx2a < pIdx1) pIdx2a else pIdx2a + 1  // so pIdx1 != pIdx2
      val p1      = gs( (i << 1)      % gs.size) // genome(pIdx1)
      val p2      = gs(((i << 1) + 1) % gs.size) // genome(pIdx2)

      val top1    = p1.top
      val top2    = p2.top
      val v1      = top1.vertices
      val v2      = top2.vertices

      val (pos1, pos2) = if (coin(0.8)) {   // XXX TODO -- make that a parameter
        val posRel  = rnd.nextFloat()
        val _pos1   = (posRel * v1.size - 1).toInt + 1
        val _pos2   = (posRel * v2.size - 1).toInt + 1
        (_pos1, _pos2)
      } else {
        val posRel1 = rnd.nextFloat()
        val _pos1   = (posRel1 * v1.size - 1).toInt + 1
        val posRel2 = rnd.nextFloat()
        val _pos2   = (posRel2 * v2.size - 1).toInt + 1
        (_pos1, _pos2)
      }

      val (head1, tail1)  = v1.splitAt(pos1)
      val (head2, tail2)  = v2.splitAt(pos2)
      val edgesHead1      = top1.edges.filter(e => head1.contains(e.sourceVertex) && head1.contains(e.targetVertex))
      val edgesTail1      = top1.edges.filter(e => tail1.contains(e.sourceVertex) && tail1.contains(e.targetVertex))
      val edgesHead2      = top2.edges.filter(e => head2.contains(e.sourceVertex) && head2.contains(e.targetVertex))
      val edgesTail2      = top2.edges.filter(e => tail2.contains(e.sourceVertex) && tail2.contains(e.targetVertex))

      val severedHeads1   = top1.edges.collect {
        case Edge(source: Vertex.UGen, target, _) if head1.contains(source) && tail1.contains(target) => source
      }
      val severedHeads2   = top2.edges.collect {
        case Edge(source: Vertex.UGen, target, _) if head2.contains(source) && tail2.contains(target) => source
      }

      @tailrec def shrinkTop(top: Top, target: Int, iter: Int): Top =
        if (top.vertices.size <= target || iter == global.maxNumVertices) top else {
          val (top1, _) = MutationImpl.removeVertex1(top)
          shrinkTop(top1, target = target, iter = iter + 1)
        }

      def mkTop(vertices1: Vec[Vertex], edges1: Set[Edge], vertices2: Vec[Vertex], edges2: Set[Edge]): Top = {
        val t1a = (Topology.empty[Vertex, Edge] /: vertices1)(_ addVertex _)
        val t1b = (t1a /: edges1)(_.addEdge(_).get._1)  // this is now the first half of the original top

        val (t2a, e2cpy) = ((t1b, edges2) /: vertices2) { case ((t0, e0), v0) =>
          // two parents might share the same vertices from a common
          // ancestry; in that case we must individualize the vertex
          // (making a copy means they get fresh object identity and hash)
          val isNew = !vertices1.contains(v0)
          val v     = if (isNew) v0 else v0.copy()
          val tRes  = t0.addVertex(v)
          val eRes  = if (isNew) e0 else e0.map { e =>
            if      (e.sourceVertex == v0) e.copy(sourceVertex = v)
            else if (e.targetVertex == v0) e.copy(targetVertex = v)
            else e
          }
          (tRes, eRes)
        }
        (t2a /: e2cpy) { (t0, e0) =>
          val res = t0.addEdge(e0)
          if (res.isFailure) {
            println("WARNING: Cross-over mkTop - cycle detected!")
          }
          res.toOption.fold(t0)(_._1)
        }
      }

      val topC1a = mkTop(head1, edgesHead1, tail2, edgesTail2)
      val topC2a = mkTop(head2, edgesHead2, tail1, edgesTail1)

      //      def completeOLD(top: Top): Top = {
      //        val inc = top.vertices.collect {
      //          case v: Vertex.UGen if ChromosomeImpl.findIncompleteUGenInputs(top, v).nonEmpty => v
      //        }
      //        val top1 = if (inc.isEmpty) top else (top /: inc)((res, v) => ChromosomeImpl.completeUGenInputs(res, v))
      //        val top2 = shrinkTop(top1, top.vertices.size, 0)
      //        top2
      //      }

      def complete(top: Top, inc: Set[Vertex.UGen]): Top = {
        val top1 = if (inc.isEmpty) top else (top /: inc)((res, v) => ChromosomeImpl.completeUGenInputs(res, v))
        val top2 = shrinkTop(top1, top.vertices.size, 0)
        top2
      }

      val topC1 = complete(topC1a, severedHeads1)
      val topC2 = complete(topC2a, severedHeads2)

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