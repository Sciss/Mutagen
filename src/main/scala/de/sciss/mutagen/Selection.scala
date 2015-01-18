package de.sciss.mutagen

import de.sciss.muta
import de.sciss.muta.{SelectionPercent, SelectionSize}

case class Selection(size: SelectionSize = SelectionPercent(25))
  extends muta.Selection[Chromosome] with muta.impl.SelectionRouletteImpl[Chromosome]