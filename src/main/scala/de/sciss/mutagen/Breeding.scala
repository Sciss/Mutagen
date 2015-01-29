package de.sciss.mutagen

import de.sciss.muta
import de.sciss.muta.{BreedingFunction, SelectionPercent, SelectionNumber, SelectionSize}

import scala.util.Random

case class Breeding(elitism: SelectionSize = SelectionNumber(4), mutationIter: Int = 2)
  extends muta.Breeding[Chromosome, Global] with muta.impl.BreedingImpl[Chromosome, Global] {

  //    def apply(sel: Vec[(Chromosome, Double, Boolean)], g: Global, rnd: Random): Vec[Chromosome] = {
  //      Vec.tabulate(g.population)(i => sel(i % sel.size)._1)
  //    }

  val crossoverWeight: SelectionPercent = SelectionPercent(0)  // XXX TODO

  /** Override to allow a population to grow or shrink dynamically if the process
    * is interrupted and the settings changed.
    */
  override def apply(g: Vec[(Chromosome, Double, Boolean)], global: Global, r: util.Random): Vec[Chromosome] = {
    val g1 = if (g.size == global.population) g else Vector.tabulate(global.population)(i => g(i % g.size))
    super.apply(g1, global, r)
  }

  object crossover extends BreedingFunction[Chromosome, Global] {
    def apply(genome: Vec[Chromosome], sz: Int, global: Global, rnd: Random): Vec[Chromosome] = {
      ???
    }
  }

  val mutation: BreedingFunction[Chromosome, Global] = new impl.MutationImpl(mutationIter)
}
