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
import de.sciss.processor.{GenericProcessor, ProcessorFactory}
import de.sciss.synth.SynthGraph
import impl.{MutagenImpl => Impl}

import scala.collection.immutable.{IndexedSeq => Vec}
import scala.language.implicitConversions

object Mutagen extends ProcessorFactory {
  sealed trait ConfigLike {
    def in: File
    def population: Int
    def iterations: Int
    def elitism: Int
  }
  object Config {
    def apply(): ConfigBuilder = new ConfigBuilder
    implicit def build(b: ConfigBuilder): Config = b.build
  }
  case class Config private[Mutagen] (in: File, population: Int, iterations: Int, elitism: Int)
    extends ConfigLike

  final class ConfigBuilder private[Mutagen] () extends ConfigLike {
    var in: File    = file("in")
    var population  = 50
    var iterations  = 10
    var elitism     = 2

    def build: Config = Config(in = in, population = population, iterations = iterations, elitism = elitism)
  }

  protected def prepare(config: Config): Prepared = new Impl(config)

  type Repr     = GenericProcessor[Product]
  type Product  = Vec[SynthGraph]
}
