/*
 *  Mutagen.scala
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

import de.sciss.file._
import de.sciss.processor.{Processor, GenericProcessor, ProcessorFactory}
import de.sciss.synth.SynthGraph
import de.sciss.mutagen.impl.{MutagenImpl => Impl}

import scala.collection.immutable.{IndexedSeq => Vec}
import scala.language.implicitConversions

object Mutagen extends ProcessorFactory {
  sealed trait ConfigLike {
    def in: File
    def population: Int
    def iterations: Int
    def elitism: Int
    def seed: Long
  }
  object Config {
    def apply(): ConfigBuilder = new ConfigBuilder
    implicit def build(b: ConfigBuilder): Config = b.build
  }
  case class Config private[Mutagen] (in: File, population: Int, iterations: Int, elitism: Int, seed: Long)
    extends ConfigLike

  final class ConfigBuilder private[Mutagen] () extends ConfigLike {
    var in: File    = file("in")
    var population  = 50
    var iterations  = 10
    var elitism     = 2
    var seed        = System.currentTimeMillis()

    def build: Config = Config(in = in, population = population, iterations = iterations, elitism = elitism,
      seed = seed)
  }

  protected def prepare(config: Config): Prepared = new Impl(config)

  type Repr     = Mutagen
  type Product  = Vec[Chromosome]
}
trait Mutagen extends Processor[Mutagen.Product, Mutagen] {
  // implicit def random: util.Random
}