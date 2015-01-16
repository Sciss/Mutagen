/*
 *  Global.scala
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

import de.sciss.file._

case class Global(input: File = file("input.aif"), population: Int = 50, seed: Int = 0,
                  constProb     : Double = 0.5,
                  minNumVertices: Int    = 4,
                  maxNumVertices: Int    = 50,
                  nonDefaultProb: Double = 0.9 // 0.5
)