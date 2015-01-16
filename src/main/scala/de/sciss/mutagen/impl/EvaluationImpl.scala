/*
 *  EvaluationImpl.scala
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
package impl

import de.sciss.file._
import de.sciss.filecache
import de.sciss.filecache.{MutableConsumer, MutableProducer}
import de.sciss.serial.{DataOutput, DataInput, ImmutableSerializer}
import de.sciss.strugatzki.FeatureExtraction
import de.sciss.synth.io.AudioFile

import scala.concurrent.{Await, Future, blocking}
import scala.concurrent.duration.Duration

object EvaluationImpl {
  private object CacheValue {
    implicit object serializer extends ImmutableSerializer[CacheValue] {
      def write(v: CacheValue, out: DataOutput): Unit = {
        out.writeLong(v.lastModified)
        out.writeUTF (v.meta   .getCanonicalPath)
        out.writeUTF (v.feature.getCanonicalPath)
      }

      def read(in: DataInput): CacheValue = {
        val mod     = in.readLong()
        val meta    = file(in.readUTF())
        val feature = file(in.readUTF())
        CacheValue(lastModified = mod, meta = meta, feature = feature)
      }
    }
  }
  private case class CacheValue(lastModified: Long, meta: File, feature: File)

  private val cCfg  = {
    val c = filecache.Config[File, CacheValue]()
    c.capacity  = filecache.Limit(count = 10)
    c.accept    = { (key, value) => key.lastModified() == value.lastModified }
    c.space     = { (key, value) => value.meta.length() + value.feature.length() }
    c.evict     = { (key, value) => value.meta.delete() ; value.feature.delete() }
    c.build
  }
  private val cacheP = MutableProducer(cCfg)
  private val cache  = MutableConsumer(cacheP)(mkCacheValue)

  import cacheP.executionContext

  private def mkCacheValue(key: File): Future[CacheValue] = {
    val inputSpec         = AudioFile.readSpec(key)
    val inputMod          = key.lastModified()
    require(inputSpec.numChannels == 1, s"Input file '${key.name}' must be mono but has ${inputSpec.numChannels} channels")
    val exCfg             = FeatureExtraction.Config()
    exCfg.audioInput      = key
    val inputFeature      = File.createTemp(suffix = ".aif")
    exCfg.featureOutput   = inputFeature
    val inputExtr         = File.createTemp(suffix = "_feat.xml")
    exCfg.metaOutput      = Some(inputExtr)
    val futInputExtr      = FeatureExtraction(exCfg)
    futInputExtr.start()
    futInputExtr.map { _ =>
      CacheValue(lastModified = inputMod, meta = inputExtr, feature = inputFeature)
    }
  }

  def apply(c: Chromosome, eval: Evaluation, g: Global): Double = {
    val key     = g.input
    val futMeta = cache.acquire(key)
    val futEval = futMeta.flatMap { v =>
      val inputExtr = v.meta
      val inputSpec = blocking(AudioFile.readSpec(key))
      implicit val global = g
      c.evaluate(eval, inputSpec, inputExtr)
    }
    val fitness = Await.result(futEval, Duration.Inf).fitness
    cache.release(key)
    fitness
  }
}
