/*
 *  MutationImpl.scala
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
import de.sciss.mutagen.MutagenSystem.Global

import scala.annotation.{tailrec, switch}
import scala.collection.immutable.{IndexedSeq => Vec}
import scala.util.Random

object MutationImpl {
  private val stats = Array.fill(7)(0)

  def printStats(): Unit = println(stats.mkString(", "))

  private def getTargets(top: Top, v: Vertex): Set[Edge] =
    top.edges.collect {
      case e @ Edge(_, `v`, _) => e // a vertex `vi` that uses the removed vertex as one of its inlets
    }

  def removeVertex1(top: Top)(implicit random: Random, global: Global): (Top, Vertex) = {
    val vertices    = top.vertices
    val numVertices = vertices.size
    val idx         = random.nextInt(numVertices)
    val v           = vertices(idx)
    val targets     = getTargets(top, v)
    val top1        = top.removeVertex(v)
    val top3        = (top1 /: targets) { (top2, e) =>
      val x = top2.removeEdge(e)
      assert(x ne top2)
      x
    }
    val succ = (top3 /: targets) { case (top4, Edge(t: Vertex.UGen, _, _)) =>
      ChromosomeImpl.completeUGenInputs(top4, t)
    }
    (succ, v)
  }
}
class MutationImpl(mutMin: Int, mutMax: Int) extends BreedingFunction[Chromosome, Global] {
  import MutationImpl._

  def apply(genome: Vec[Chromosome], sz: Int, glob: Global, rnd: Random): Vec[Chromosome] = {
    implicit val random = rnd
    implicit val global = glob

    val mutationIter = Util.rrand(mutMin, mutMax)

    // XXX bullshit --- mutationIter has not influence, because `pred` is not used!!!
    @tailrec def loop(iter: Int, pred: Vec[Chromosome]): Vec[Chromosome] = if (iter >= mutationIter) pred else {
      var res = Vector.empty[Chromosome]
      while (res.size < sz) {
        val picked = genome(res.size % genome.size)
        res = (rnd.nextInt(7): @switch) match {
          case 0 => addVertex   (picked).fold(res)(res :+ _)
          case 1 => removeVertex(picked).fold(res)(res :+ _)
          case 2 => res :+ changeVertex(picked)
          case 3 => changeEdge  (picked).fold(res)(res :+ _)
          case 4 => swapEdge    (picked).fold(res)(res :+ _)
          case 5 => splitVertex (picked).fold(res)(res :+ _)
          case 6 => mergeVertex (picked).fold(res)(res :+ _)
        }
      }
      loop(iter + 1, res)
    }

    loop(0, genome)
  }

  private def roulette[A](in: Vec[(A, Int)])(implicit random: util.Random): A = {
    val sum         = in.map(_._2).sum
    val norm        = in.zipWithIndex.map { case ((c, f), j) => (j, f / sum) }
    val sorted      = norm.sortBy(_._2)
    val accum       = sorted.scanLeft(0.0) { case (a, (_, f)) => a + f } .tail
    val roul        = random.nextDouble() // * max
    val idxS        = accum.indexWhere(_ > roul)
    val idx         = if (idxS >= 0) sorted(idxS)._1 else in.size - 1
    val (chosen, _) = in(idx)
    chosen
  }

  private def addVertex(pred: Chromosome)(implicit random: Random, global: Global): Option[Chromosome] = {
    val top = pred.top
    if (top.vertices.size >= global.maxNumVertices) None else {
      val succ = ChromosomeImpl.addVertex(top)
      val res  = new Chromosome(succ, seed = random.nextLong())
      checkComplete(succ, s"addVertex()")
      stats(0) += 1
      Some(res)
    }
  }

  private def removeVertex(pred: Chromosome)(implicit random: Random, global: Global): Option[Chromosome] = {
    val top         = pred.top
    val vertices    = top.vertices
    val numVertices = vertices.size
    if (numVertices <= global.minNumVertices) None else {
      val (succ, v)   = removeVertex1(top)
      val res         = new Chromosome(succ, seed = random.nextLong())
      checkComplete(succ, s"removeVertex($v)")
      stats(1) += 1
      Some(res)
    }
  }

  private def changeVertex(pred: Chromosome)(implicit random: Random, global: Global): Chromosome = {
    val top         = pred.top
    val vertices    = top.vertices
    val numVertices = vertices.size

    val idx     = random.nextInt(numVertices)
    val vOld    = vertices(idx)
    val outlet  = getTargets(top, vOld)
    val inlets  = top.edgeMap.getOrElse(vOld, Set.empty)
    val top1    = (top  /: outlet)(_ removeEdge _)
    val top2    = (top1 /: inlets)(_ removeEdge _)
    val top3    = top2.removeVertex(vOld)

    val vNew    = vOld match {
      case Vertex.Constant(f) =>
        if (Util.coin())
          ChromosomeImpl.mkConstant()   // completely random
        else
          Vertex.Constant(f * Util.exprand(0.9, 1.0/0.9).toFloat) // gradual change
      case _ =>
        ChromosomeImpl.mkUGen()
    }

    val oldInletNames: Vec[String] = vOld match {
      case Vertex.UGen(info) => /* ChromosomeImpl.geArgs(info).map(_.name) */ info.inputs.map(_.arg)
      case _ => Vec.empty
    }
    val newInletNames: Vec[String] = vNew match {
      case Vertex.UGen(info) => /* ChromosomeImpl.geArgs(info).map(_.name) */ info.inputs.map(_.arg)
      case _ => Vec.empty
    }

    val top4  = top3.addVertex(vNew)
    val top5  = (top4 /: outlet.map(_.copy(targetVertex = vNew)))((t, e) => t.addEdge(e).get._1)

    // just as many as possible, leaving tail inlets empty
    val newInlets = inlets.collect {
      case e if oldInletNames.indexOf(e.inlet) < newInletNames.size =>
        e.copy(sourceVertex = vNew, inlet = newInletNames(oldInletNames.indexOf(e.inlet)))
    }

    val top6  = (top5 /: newInlets)((t, e) => t.addEdge(e).get._1)
    val top7  = vNew match {
      case vu: Vertex.UGen => ChromosomeImpl.completeUGenInputs(top6, vu)
      case _ => top6
    }

    val res = new Chromosome(top7, seed = random.nextLong())
    stats(2) += 1
    res
  }

  private def changeEdge(pred: Chromosome)(implicit random: Random, global: Global): Option[Chromosome] = {
    val top         = pred.top
    val vertices    = top.vertices

    val candidates  = vertices.collect {
      case v @ Vertex.UGen(spec) if ChromosomeImpl.geArgs(spec).nonEmpty /* spec.inputs.nonEmpty */ => v
    }

    if (candidates.isEmpty) None else {
      val v     = Util.choose(candidates)
      val edges = top.edgeMap.getOrElse(v, Set.empty)
      val top1  = if (edges.isEmpty) top else top.removeEdge(Util.choose(edges))
      val top2  = ChromosomeImpl.completeUGenInputs(top1, v)
      if (top2 == top) None else {
        val res = new Chromosome(top2, seed = random.nextLong())
        stats(3) += 1
        Some(res)
      }
    }
  }

  private def swapEdge(pred: Chromosome)(implicit random: Random, global: Global): Option[Chromosome] = {
    val top         = pred.top
    val vertices    = top.vertices

    val candidates  = vertices.collect {
      case v @ Vertex.UGen(spec) if top.edgeMap.get(v).exists(_.size >= 2) => v
    }

    if (candidates.isEmpty) None else {
      val v     = Util.choose(candidates)
      val edges = top.edgeMap.getOrElse(v, Set.empty)
      val e1    = Util.choose(edges)
      val e2    = Util.choose(edges - e1)
      val top1  = top .removeEdge(e1)
      val top2  = top1.removeEdge(e2)
      val e1New = e1.copy(targetVertex = e2.targetVertex)
      val e2New = e2.copy(targetVertex = e1.targetVertex)
      val top3  = top2.addEdge(e1New).get._1
      val top4  = top3.addEdge(e2New).get._1
      val res   = new Chromosome(top4, seed = random.nextLong())
      stats(4) += 1
      Some(res)
    }
  }

  // splits a vertex. candidates are vertices with out degree >= 2.
  // candidate is chosen using roulette algorithm (thus more likely,
  // the higher the out degree).
  private def splitVertex(pred: Chromosome)(implicit random: Random, global: Global): Option[Chromosome] = {
    val top         = pred.top
    val verticesIn  = top.vertices
    val numVertices = verticesIn.size
    if (numVertices >= global.maxNumVertices) return None

    val weighted  = verticesIn.flatMap { v =>
      val set = top.edges.filter(_.targetVertex == v)
      val sz  = set.size
      if (sz > 2) Some(set -> sz) else None
    }
    if (weighted.isEmpty) None else {
      val edges = roulette(weighted)
      import kollflitz.RandomOps._
      val edgesS: Vec[Edge] = edges.toVector.scramble()
      val (_, edgesMove) = edgesS.splitAt(edgesS.size/2)
      val vertexOld = edges.head.targetVertex
      val vertexNew = vertexOld.copy()
      val top1 = (top /: edgesMove)(_ removeEdge _)
      val top2 = top1.addVertex(vertexNew)
      val top3 = (top2 /: edgesMove) { (t, eOld) =>
        val eNew = eOld.copy(targetVertex = vertexNew)
        t.addEdge(eNew).get._1
      }
      val succ = (top3 /: top.edgeMap.getOrElse(vertexOld, Set.empty)) { (t, eOld) =>
        val eNew = eOld.copy(sourceVertex = vertexNew)
        t.addEdge(eNew).get._1
      }
      val res = new Chromosome(succ, seed = random.nextLong())
      checkComplete(succ, s"splitVertex()")
      stats(5) += 1
      Some(res)
    }
  }

  // merges two vertices of the same type
  private def mergeVertex(pred: Chromosome)(implicit random: Random, global: Global): Option[Chromosome] = {
    val top         = pred.top
    val verticesIn  = top.vertices
    val numVertices = verticesIn.size
    if (numVertices <= global.minNumVertices) return None

    import kollflitz.RandomOps._
    val it = verticesIn.scramble().tails.flatMap {
      case head +: tail =>
        val partners = head match {
          case Vertex.Constant(_) => tail.filter {
            case Vertex.Constant(_) => true // any two constants will do
            case _ => false
          }
          case Vertex.UGen(info) => tail.filter {
            case Vertex.UGen(i1) => i1.name == info.name
            case _ => false
          }
        }
        partners.map(head -> _)

      case _ => None
    }
    if (it.isEmpty) None else {
      val (v1, v2)  = it.next()
      val edgesOld  = top.edges.filter(_.targetVertex == v2)
      val top1      = (top  /: edgesOld)(_ removeEdge _)
      val succ      = (top1 /: edgesOld) { (t, eOld) =>
        val eNew = eOld.copy(targetVertex = v1)
        if (t.canAddEdge(eNew)) t.addEdge(eNew).get._1 else t
      }
      val res = new Chromosome(succ, seed = random.nextLong())
      checkComplete(succ, s"mergeVertex()")
      stats(6) += 1
      Some(res)
    }
  }

  private def checkComplete(succ: Top, message: => String): Unit =
    succ.vertices.foreach {
      case v: Vertex.UGen =>
        val inc = ChromosomeImpl.findIncompleteUGenInputs(succ, v)
        if (inc.nonEmpty) {
          println("MISSING SLOTS:")
          inc.foreach(println)
          sys.error(s"UGen is not complete: $v - $message")
        }
      case _ =>
    }

  /*
    ways to mutate:

    - add, remove or alter edges


    - add, remove or alter vertices
    - constant vertex: change value
    - ugen     vertex: exchange?

   */
}