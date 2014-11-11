/*
 *  MutagenImpl.scala
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
package impl

import de.sciss.processor.GenericProcessor
import de.sciss.processor.impl.ProcessorImpl

final class MutagenImpl(config: Mutagen.Config)
  extends ProcessorImpl[Mutagen.Product, Mutagen.Repr] with GenericProcessor[Mutagen.Product] {

  protected def body(): Mutagen.Product = {
    // 1. analyze input using Strugatzki
    // 2. generate initial random population
    // 3. render each chromosome using offline server
    // 4. analyze and compare each chromosome's audio output using Strugatzki
    // 5. rank, select, mutate
    // 6. iterate from 3 for number of iterations
    // 7. return resulting population as SynthGraph

    ???
  }
}
