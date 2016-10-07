/*
 *  Generation.scala
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

import de.sciss.muta

import scala.util.Random

case class Generation(global: Global = Global()) extends muta.Generation[Chromosome, Global] {
  def size: Int = global.population
  def seed: Int = global.seed

  def apply(random: Random): Chromosome = Chromosome()(random, global)
}

