/*
 *  Breeding.scala
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

import de.sciss.muta
import de.sciss.muta.{BreedingFunction, SelectionPercent, SelectionNumber, SelectionSize}

import scala.util.Random

case class Breeding(elitism: SelectionSize = SelectionNumber(4),
                    crossoverWeight: SelectionPercent = SelectionPercent(50),
                    mutationIter: Int = 2)
  extends muta.Breeding[Chromosome, Global] with muta.impl.BreedingImpl[Chromosome, Global] {

  /** Override to allow a population to grow or shrink dynamically if the process
    * is interrupted and the settings changed.
    */
  override def apply(g: Vec[(Chromosome, Double, Boolean)], global: Global, r: util.Random): Vec[Chromosome] = {
    val g1 = if (g.size == global.population) g else Vector.tabulate(global.population)(i => g(i % g.size))
    super.apply(g1, global, r)
  }

  def crossover: BreedingFunction[Chromosome, Global] = impl.CrossoverImpl

  val mutation: BreedingFunction[Chromosome, Global] = new impl.MutationImpl(mutationIter)
}
