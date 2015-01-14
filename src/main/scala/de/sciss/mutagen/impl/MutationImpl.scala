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

import scala.annotation.switch
import scala.collection.immutable.{IndexedSeq => Vec}
import scala.util.Random

object MutationImpl extends BreedingFunction[Chromosome, Global] {
  def apply(genome: Vec[Chromosome], sz: Int, glob: Global, rnd: Random): Vec[Chromosome] = {
    var res = Vector.empty[Chromosome]
    implicit val random = rnd
    implicit val global = glob
    while (res.size < sz) {
      val picked = genome(res.size % genome.size)
      res = (rnd.nextInt(2): @switch) match {
        case 0 => addVertex   (picked).fold(res)(res :+ _)
        case 1 => removeVertex(picked).fold(res)(res :+ _)
        //        case 2 => addEdge()
        //        case 3 => removeEdge()
        //        case 4 => changeEdge()
        //        case 5 => changeConstant()
      }
    }
    res
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
      val targets = top.edges.collect {
        case e @ Edge(vi: Vertex.UGen, `v`, _) => e // a vertex `vi` that uses the removed vertex as one of its inlets
      }
      val top1 = top.removeVertex(v)
      val top3 = (top1 /: targets) { (top2, e) =>
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

  private def changeConstant() = ???

  private def addEdge() = ???

  private def removeEdge() = ???

  private def changeEdge() = ???

  /*
    ways to mutate:

    - add, remove or alter edges


    - add, remove or alter vertices
    - constant vertex: change value
    - ugen     vertex: exchange?

   */
}