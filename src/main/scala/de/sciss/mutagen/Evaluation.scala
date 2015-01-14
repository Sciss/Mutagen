package de.sciss.mutagen

import de.sciss.muta

case class Evaluation(normalize: Boolean = false, maxBoost: Double = 8.0, temporalWeight: Double = 0.5)
  extends muta.Evaluation[Chromosome, Global] {

  def apply(c: Chromosome, glob: Global): Double = impl.EvaluationImpl(c, this, glob)
}
