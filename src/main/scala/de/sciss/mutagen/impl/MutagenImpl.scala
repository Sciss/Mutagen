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

import de.sciss.file._
import de.sciss.processor.GenericProcessor
import de.sciss.processor.impl.ProcessorImpl
import de.sciss.strugatzki.FeatureExtraction
import de.sciss.synth.io.AudioFile

final class MutagenImpl(config: Mutagen.Config)
  extends ProcessorImpl[Mutagen.Product, Mutagen.Repr] with GenericProcessor[Mutagen.Product] {

  protected def body(): Mutagen.Product = {
    // 1. analyze input using Strugatzki
    // 2. generate initial random population
    //
    // 3. render each chromosome using offline server
    // 4. analyze and compare each chromosome's audio output using Strugatzki
    // 5. rank, select, mutate
    // 6. iterate from 3 for number of iterations
    //
    // 7. return resulting population as SynthGraph

    val spec  = AudioFile.readSpec(config.in)
    require(spec.numChannels == 1, s"Input file '${config.in.name}' must be mono but has ${spec.numChannels} channels")
    val exCfg             = FeatureExtraction.Config()
    exCfg.audioInput      = config.in
    exCfg.featureOutput   = File.createTemp(suffix = ".xml")
    val ex                = FeatureExtraction(exCfg)
    ex.start()

    // we could use proc.Topology
    // to represent the synth graph
    // (allowing cycle and orphan detection?)

    ???
  }
}
