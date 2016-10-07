/*
 *  Selection.scala
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
import de.sciss.muta.{SelectionPercent, SelectionSize}

case class Selection(size: SelectionSize = SelectionPercent(25))
  extends muta.Selection[Chromosome] with muta.impl.SelectionRouletteImpl[Chromosome]