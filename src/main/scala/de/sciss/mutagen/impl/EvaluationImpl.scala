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

import java.util.concurrent.TimeUnit

import de.sciss.file._
import de.sciss.filecache
import de.sciss.filecache.{MutableConsumer, MutableProducer}
import de.sciss.serial.{DataOutput, DataInput, ImmutableSerializer}
import de.sciss.strugatzki.FeatureExtraction
import de.sciss.synth.io.{AudioFileSpec, AudioFile}

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

  def getInputSpec(c: Chromosome)(implicit global: Global): Future[(File, AudioFileSpec)] = {
    val key       = global.input
    val futMeta   = cache.acquire(key)
    val res       = futMeta.map { v =>
      val inputExtr = v.meta
      val inputSpec = blocking(AudioFile.readSpec(key))
      (inputExtr, inputSpec)
    }
    res.onComplete { case _ => cache.release(key) }
    res
  }

  def apply(c: Chromosome, eval: Evaluation)(implicit global: Global): Double = {
    val futEval = getInputSpec(c).flatMap { case (inputExtr, inputSpec) =>
      c.evaluate(eval, inputSpec, inputExtr)
    }
    val fitness = Await.result(futEval, Duration(24, TimeUnit.SECONDS) /* Duration.Inf */).fitness
    fitness
  }
}
