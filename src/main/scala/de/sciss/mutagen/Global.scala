package de.sciss.mutagen

import de.sciss.file._

case class Global(input: File = file("input.aif"), population: Int = 50, seed: Int = 0,
                  constProb     : Double = 0.5,
                  minNumVertices: Int    = 4,
                  maxNumVertices: Int    = 50,
                  nonDefaultProb: Double = 0.9 // 0.5
)