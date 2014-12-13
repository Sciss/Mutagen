/*
 *  MutagenImpl.scala
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
package impl

import de.sciss.file._
import de.sciss.processor.GenericProcessor
import de.sciss.processor.impl.ProcessorImpl
import de.sciss.strugatzki.FeatureExtraction
import de.sciss.synth.{UndefinedRate, UGenSpec}
import de.sciss.synth.io.AudioFile
import de.sciss.topology.Topology

import scala.annotation.tailrec
import scala.collection.immutable.{IndexedSeq => Vec}

final class MutagenImpl(config: Mutagen.Config)
  extends ProcessorImpl[Mutagen.Product, Mutagen.Repr] with GenericProcessor[Mutagen.Product] {

  private val rnd = new util.Random(config.seed)

  protected def body(): Mutagen.Product = {
    // outline of algorithm:
    // 1. analyze input using Strugatzki
    // 2. generate initial random population
    //
    // 3. render each chromosome using offline server
    // 4. analyze and compare each chromosome's audio output using Strugatzki
    // 5. rank, select, mutate
    // 6. iterate from 3 for number of iterations
    //
    // 7. return resulting population as SynthGraph

    // -- 1 --
    // analyze input using Strugatzki
    val spec  = AudioFile.readSpec(config.in)
    require(spec.numChannels == 1, s"Input file '${config.in.name}' must be mono but has ${spec.numChannels} channels")
    val exCfg             = FeatureExtraction.Config()
    exCfg.audioInput      = config.in
    exCfg.featureOutput   = File.createTemp(suffix = ".xml")
    val ex                = FeatureExtraction(exCfg)
    ex.start()

    // -- 2 --
    // generate initial random population
    val pop0  = Vector.fill(config.population)(mkIndividual())

    ???
  }

  private val NoNoAttr: Set[UGenSpec.Attribute] = {
    import UGenSpec.Attribute._
    Set(HasSideEffect, ReadsBuffer, ReadsBus, ReadsFFT, WritesBuffer, WritesBus, WritesFFT)
  }

  private val ugens: Vec[UGenSpec] = UGenSpec.standardUGens.valuesIterator.filter { spec =>
    spec.attr.intersect(NoNoAttr).isEmpty
  } .toIndexedSeq

  private val constProb       = 0.5
  private val minNumVertices  = 4
  private val maxNumVertices  = 100
  private val nonDefaultProb  = 0.5

  // ---- random functions ----
  // cf. https://github.com/Sciss/Dissemination/blob/master/src/main/scala/de/sciss/semi/Util.scala

  private def rrand  (lo: Int   , hi: Int   ): Int    = lo + rnd.nextInt(hi - lo + 1)
  private def exprand(lo: Double, hi: Double): Double = lo * math.exp(math.log(hi / lo) * rnd.nextDouble())

  private def coin(p: Double = 0.5): Boolean = rnd.nextDouble() < p

  private def choose[A](xs: Vec[A]): A = xs(rnd.nextInt(xs.size))

  // ----

  private type Top = Topology[Vertex, Edge]

  private def mkConstant(): Vertex.Constant = {
    val f0  = exprand(0.001, 10000.001) - 0.001
    val f   = if (coin(0.25)) -f0 else f0
    val v   = Vertex.Constant(f.toFloat)
    v
  }

  private def mkIndividual(): Chromosome = {
    val num = rrand(minNumVertices, maxNumVertices)

    @tailrec def loopGraph(pred: Top): Top =
      if (pred.vertices.size >= num) pred else {
        val next: Top = if (coin(constProb)) {
          val v   = mkConstant()
          pred.addVertex(v)

        } else {
          val spec  = choose(ugens)
          val v     = Vertex.UGen(spec)
          val t1    = pred.addVertex(v)
          val (hasDef, hasNoDef)  = spec.args.partition(_.defaults.contains(UndefinedRate))
          val (useNotDef, useDef) = hasDef.partition(_ => coin(nonDefaultProb))
          val findDef = hasNoDef ++ useNotDef

          @tailrec def loopVertex(rem: Vec[UGenSpec.Argument], pred: Top): Top = rem match {
            case head +: tail =>
              val options = pred.vertices.filter { vi =>
                val e = Edge(vi, v, head.name)
                pred.canAddEdge(e)
              }
              val next: Top = if (options.nonEmpty) {
                val vi  = choose(options)
                val e   = Edge(vi, v, head.name)
                pred.addEdge(e).get._1
              } else {
                val c   = mkConstant()
                val n0  = pred.addVertex(c)
                val e   = Edge(c, v, head.name)
                n0.addEdge(e).get._1
              }

              loopVertex(tail, next)

            case _ => pred
          }

          loopVertex(findDef, t1)
        }

        loopGraph(next)
      }

    val t0 = loopGraph(Topology.empty)
    new Chromosome(t0)
  }
}
