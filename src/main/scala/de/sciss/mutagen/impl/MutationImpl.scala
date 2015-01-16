/*
 *  MutationImpl.scala
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

import de.sciss.muta.BreedingFunction
import de.sciss.mutagen.MutagenSystem.Global

import scala.annotation.{tailrec, switch}
import scala.collection.immutable.{IndexedSeq => Vec}
import scala.util.Random

class MutationImpl(mutationIter: Int) extends BreedingFunction[Chromosome, Global] {
  def apply(genome: Vec[Chromosome], sz: Int, glob: Global, rnd: Random): Vec[Chromosome] = {
    implicit val random = rnd
    implicit val global = glob

    @tailrec def loop(iter: Int, pred: Vec[Chromosome]): Vec[Chromosome] = if (iter >= mutationIter) pred else {
      var res = Vector.empty[Chromosome]
      while (res.size < sz) {
        val picked = genome(res.size % genome.size)
        res = (rnd.nextInt(3): @switch) match {
          case 0 => addVertex   (picked).fold(res)(res :+ _)
          case 1 => removeVertex(picked).fold(res)(res :+ _)
          case 2 => res :+ changeVertex(picked)
          //        case 2 => addEdge()
          //        case 3 => removeEdge()
          //        case 4 => changeEdge()
          //        case 5 => changeConstant()
        }
      }
      loop(iter + 1, res)
    }

    loop(0, genome)
  }

  private def addVertex(pred: Chromosome)(implicit random: Random, global: Global): Option[Chromosome] = {
    val top = pred.top
    if (top.vertices.size >= global.maxNumVertices) None else {
      val succ = ChromosomeImpl.addVertex(top)
      val res  = new Chromosome(succ, seed = random.nextLong())
      checkComplete(succ, s"addVertex()")
      Some(res)
    }
  }

  private def checkComplete(succ: Top, message: => String): Unit = {
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
  }

  private def removeVertex(pred: Chromosome)(implicit random: Random, global: Global): Option[Chromosome] = {
    val top         = pred.top
    val vertices    = top.vertices
    val numVertices = vertices.size
    if (numVertices <= global.minNumVertices) None else {
      val idx     = random.nextInt(numVertices)
      val v       = vertices(idx)
      val targets = getTargets(top, v)
      val top1    = top.removeVertex(v)
      val top3    = (top1 /: targets) { (top2, e) =>
        val x = top2.removeEdge(e)
        assert(x ne top2)
        x
      }
      val succ = (top3 /: targets) { case (top4, Edge(t: Vertex.UGen, _, _)) =>
        ChromosomeImpl.completeUGenInputs(top4, t)
      }
      val res  = new Chromosome(succ, seed = random.nextLong())
      checkComplete(succ, s"removeVertex($v)")
      Some(res)
    }
  }

  private def getTargets(top: Top, v: Vertex): Set[Edge] =
    top.edges.collect {
      case e @ Edge(_, `v`, _) => e // a vertex `vi` that uses the removed vertex as one of its inlets
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
      case Vertex.UGen(info) => info.inputs.map(_.arg)
      case _ => Vec.empty
    }
    val newInletNames: Vec[String] = vNew match {
      case Vertex.UGen(info) => info.inputs.map(_.arg)
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
    new Chromosome(top7, seed = random.nextLong())
  }

  /*
    ways to mutate:

    - add, remove or alter edges


    - add, remove or alter vertices
    - constant vertex: change value
    - ugen     vertex: exchange?

   */
}