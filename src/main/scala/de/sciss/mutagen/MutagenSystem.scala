/*
 *  MutagenSystem.scala
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
import de.sciss.guiflitz.AutoView
import de.sciss.muta.Vec
import de.sciss.play.json.AutoFormat
import de.sciss.{muta, mutagen}
import play.api.libs.json.{JsSuccess, JsError, JsString, JsResult, JsValue, Format}

import scala.reflect.{ClassTag, classTag}
import scala.util.Random

object MutagenSystem extends muta.System {
  // WARNING: must not be `implicit` to avoid false recursion
  val chromosomeClassTag: ClassTag[Chromosome] = classTag[mutagen.Chromosome]

  // ---- settings ----

  case class Global(input: File = file("input.aif"), population: Int = 50, seed: Int = 0)

  // ---- types ----

  type Chromosome = mutagen.Chromosome

  case class Generation(global: Global = Global()) extends muta.Generation[Chromosome, Global] {
    def size: Int = global.population
    def seed: Int = global.seed

    def apply(random: Random): Chromosome = Chromosome()(random)
  }

  // type Evaluation = Evaluation.type
  def Evaluation: Evaluation = impl.EvaluationImpl
  trait Evaluation extends muta.Evaluation[Chromosome, Global]

  object Selection extends muta.Selection[Chromosome] {
    def apply(eval: Vec[(Chromosome, Double)], rnd: Random): Vec[Chromosome] = ???
  }
  type Selection = Selection.type

  object Breeding extends muta.Breeding[Chromosome, Global] {
    def apply(sel: Vec[(Chromosome, Double, Boolean)], g: Global, rnd: Random): Vec[Chromosome] = ???
  }
  type Breeding = Breeding.type

  // ---- serialization ----

  def chromosomeFormat: Format[Chromosome] = impl.ChromosomeImpl.Format

  implicit object fileFormat extends Format[File] {
    def reads(json: JsValue): JsResult[File] = json match {
      case JsString(s) => JsSuccess(file(s))
      case _ => JsError("Malformed JSON - expected string")
    }

    def writes(f: File): JsValue = JsString(f.getCanonicalPath)
  }
  implicit val globalFormat: Format[Global] = AutoFormat[Global]
  def generationFormat: Format[Generation] = AutoFormat[Generation]

  val evaluationFormat: Format[Evaluation] = new SingletonFormat(Evaluation)
  val selectionFormat : Format[Selection ] = new SingletonFormat(Selection )
  val breedingFormat  : Format[Breeding  ] = new SingletonFormat(Breeding  )

  // ---- instances ----

  def defaultGeneration: Generation = Generation()

  def defaultEvaluation: Evaluation = Evaluation
  def defaultSelection : Selection  = Selection
  def defaultBreeding  : Breeding   = Breeding

  // ---- views ----

  def generationView(init: Generation, config: AutoView.Config): AutoView[Generation] = ???

  def evaluationView(init: Evaluation, config: AutoView.Config): AutoView[Evaluation] = ???

  def evaluationViewOption: Option[(Evaluation, AutoView.Config) => AutoView[Evaluation]] = Some(evaluationView)

  def selectionView (init: Selection , config: AutoView.Config): AutoView[Selection] = ???

  def breedingView  (init: Breeding  , config: AutoView.Config): AutoView[Breeding] = ???

}
