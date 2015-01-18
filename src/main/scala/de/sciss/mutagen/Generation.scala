package de.sciss.mutagen

import de.sciss.muta

import scala.util.Random

case class Generation(global: Global = Global()) extends muta.Generation[Chromosome, Global] {
  def size: Int = global.population
  def seed: Int = global.seed

  def apply(random: Random): Chromosome = Chromosome()(random, global)
}

