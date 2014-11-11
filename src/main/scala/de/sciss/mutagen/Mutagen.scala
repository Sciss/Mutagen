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

import java.io.File

import de.sciss.processor.{GenericProcessor, ProcessorFactory}
import de.sciss.synth.SynthGraph

import scala.collection.immutable.{IndexedSeq => Vec}

object Mutagen extends ProcessorFactory {
  case class Config(in: File, population: Int = 50, iterations: Int = 10, elitism: Int = 2)

  protected def prepare(config: Config): Prepared = ???

  type Repr     = GenericProcessor[Product]
  type Product  = Vec[SynthGraph]
}
