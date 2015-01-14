///*
// *  MutagenImpl.scala
// *  (Mutagen)
// *
// *  Copyright (c) 2014-2015 Hanns Holger Rutz. All rights reserved.
// *
// *  This software is published under the GNU General Public License v3+
// *
// *
// *  For further information, please contact Hanns Holger Rutz at
// *  contact@sciss.de
// */
//
//package de.sciss.mutagen
//package impl
//
//import de.sciss.file._
//import de.sciss.processor.impl.ProcessorImpl
//import de.sciss.strugatzki.FeatureExtraction
//import de.sciss.synth.io.AudioFile
//
//import scala.collection.immutable.{IndexedSeq => Vec}
//import scala.concurrent.duration.Duration
//import scala.concurrent.{Await, Future}
//
//final class MutagenImpl(val config: Mutagen.Config)
//  extends Mutagen with ProcessorImpl[Mutagen.Product, Mutagen.Repr] {
//
//  private val DEBUG = false
//
//  implicit val random = new util.Random(config.seed)
//
//  protected def body(): Vec[Evaluated] = {
//    // outline of algorithm:
//    // 1. analyze input using Strugatzki
//    // 2. generate initial random population
//    //
//    // 3. render each chromosome using offline server
//    // 4. analyze and compare each chromosome's audio output using Strugatzki
//    // 5. rank, select, mutate
//    // 6. iterate from 3 for number of iterations
//    //
//    // 7. return resulting population as SynthGraph
//
//    // -- 1 --
//    // analyze input using Strugatzki
//    val inputSpec         = AudioFile.readSpec(config.in)
//    require(inputSpec.numChannels == 1, s"Input file '${config.in.name}' must be mono but has ${inputSpec.numChannels} channels")
//    val exCfg             = FeatureExtraction.Config()
//    exCfg.audioInput      = config.in
//    exCfg.featureOutput   = File.createTemp(suffix = ".aif")
//    val inputExtr         = File.createTemp(suffix = "_feat.xml")
//    exCfg.metaOutput      = Some(inputExtr)
//    val futInputExtr      = FeatureExtraction(exCfg)
//    futInputExtr.start()
//    futInputExtr.onFailure {
//      case t => println(s"futInputExtr failed with $t")
//    }
//
//    // -- 2 --
//    // generate initial random population
//    val pop0  = Vector.fill(config.population)(Chromosome())
//
//    // make sure that `inputExtr` is ready
//    await[Unit](futInputExtr, offset = 0.0, weight = 0.01)
//
//    // -- 3 and 4 --
//    // render the chromosome as audio and calculate fitness using Strugatzki
//    val evalFut = pop0.map { c =>
//      c.evaluate(inputSpec, inputExtr)
//    }
//
//    val eval = Await.result[Vec[Evaluated]](Future.sequence(evalFut), Duration.Inf)
//
//    eval.sortBy(_.fitness).reverse
//  }
//}
