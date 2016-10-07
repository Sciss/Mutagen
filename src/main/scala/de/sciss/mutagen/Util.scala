/*
 *  Util.scala
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

object Util {
  // ---- random functions ----
  // cf. https://github.com/Sciss/Dissemination/blob/master/src/main/scala/de/sciss/semi/Util.scala

  def rrand  (lo: Int   , hi: Int   )(implicit random: util.Random): Int    =
    lo + random.nextInt(hi - lo + 1)

  def exprand(lo: Double, hi: Double)(implicit random: util.Random): Double =
    lo * math.exp(math.log(hi / lo) * random.nextDouble())

  def coin(p: Double = 0.5)(implicit random: util.Random): Boolean = random.nextDouble() < p

  def choose[A](xs: Iterable[A])(implicit random: util.Random): A = xs.toIndexedSeq(random.nextInt(xs.size))
}
