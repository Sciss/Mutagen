/*
 *  Evaluation.scala
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

import de.sciss.file.File
import de.sciss.muta
import de.sciss.synth.io.AudioFileSpec

import scala.concurrent.{ExecutionContext, Future}

object Evaluation {
  def getInputSpec(c: Chromosome)(implicit global: Global): Future[(File, AudioFileSpec)] =
    impl.EvaluationImpl.getInputSpec(c)

  def bounce(c: Chromosome, f: File) (implicit exec: ExecutionContext, global: Global): Future[Any] =
    getInputSpec(c).flatMap { case (inputExtr, inputSpec) =>
      impl.ChromosomeImpl.bounce(c, audioF = f, inputSpec = inputSpec, inputExtr = inputExtr)
    }
}

/** Evaluation settings for the GA.
 *
 * @param normalize       if `true` use feature vector normalization
 * @param maxBoost        maximum linear amplitude boost to match target sound
 * @param temporalWeight  from zero (spectral features only) via 0.5 (both equally weighted) to one (loudness contour only)
 * @param vertexPenalty   relative penalty for the number of vertices. The fitness decreases by
  *                       `numVertices.linlin(global.minNumVertices, global.maxNumVertices, 0, vertexPenalty)`,
  *                       so a value of zero means no penalty and a value of one means maximum penalty
  *                       (try to keep synth-graph small)
  */
case class Evaluation(normalize: Boolean = false, maxBoost: Double = 8.0,
                      temporalWeight: Double = 0.5, vertexPenalty: Double = 0.2)
  extends muta.Evaluation[Chromosome, Global] {

  def apply(c: Chromosome, glob: Global): Double = {
    implicit val global = glob
    impl.EvaluationImpl(c, this)
  }
}
