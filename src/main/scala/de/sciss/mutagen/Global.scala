/*
 *  Global.scala
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

import de.sciss.file._

/** Global GA parameters.
  *
  * @param input           target audio file
  * @param population      size of population
  * @param seed            initial random seed
  * @param constProb       probability between generating constants over UGens. zero means no constants,
  *                        one means only constants
  * @param minNumVertices  minimum number of vertices in the graph
  * @param maxNumVertices  maximum number of vertices in the graph
  * @param nonDefaultProb  probability of generating custom inputs for arguments that possess default values.
  *                        with zero, all default arguments of a UGen will be filled in, with one, not a single
  *                        default value will be used. XXX TODO: should be depending on argument position
  *                        (we will want front args to be customized stronger than tail args).
  */
case class Global(input: File = file("input.aif"), population: Int = 50, seed: Int = 0,
                  constProb     : Double = 0.5,
                  minNumVertices: Int    = 4,
                  maxNumVertices: Int    = 50,
                  nonDefaultProb: Double = 0.9 // 0.5
)