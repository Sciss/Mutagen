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
import de.sciss.guiflitz.{Cell, AutoView}
import de.sciss.model.Model
import de.sciss.muta.{SelectionPercent, SelectionSize, Vec}
import de.sciss.mutagen.gui.PathField
import de.sciss.play.json.AutoFormat
import de.sciss.synth.io.AudioFile
import de.sciss.{muta, mutagen}
import play.api.libs.json.{JsSuccess, JsError, JsString, JsResult, JsValue, Format}

import scala.reflect.{ClassTag, classTag}
import scala.swing.{Component, Label}
import scala.swing.event.ValueChanged
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

  object Selection extends muta.Selection[Chromosome] with muta.impl.SelectionRouletteImpl[Chromosome] {

    // def apply(eval: Vec[(Chromosome, Double)], rnd: Random): Vec[Chromosome] = ...
    def size: SelectionSize = SelectionPercent(25)
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

  def generationView(init: Generation, config: AutoView.Config): AutoView[Generation] = {
    val c2 = AutoView.Config()
    c2.read(config)
    c2.addViewFactory { (cell: Cell[File]) =>
      new PathField { pf =>
        value   = cell()
        title   = "Select Audio File"
        accept  = { f => if (AudioFile.identify(f).isDefined) Some(f) else None }

        val l: Model.Listener[File] = {
          case f => value = f
        }
        cell.addListener(l)

        listenTo(this)
        reactions += {
          case ValueChanged(_) =>
            cell.removeListener(l)
            cell() = pf.value
            cell.addListener   (l)
        }
      }
    }
    AutoView(init, c2)
  }

  def evaluationView(init: Evaluation, config: AutoView.Config): AutoView[Evaluation] = {
    val c2 = AutoView.Config()
    c2.read(config)
    c2.addViewFactory { (cell: Cell[Evaluation]) =>
      new Label("No settings.")
    }
    AutoView(init, c2)
  }

  def evaluationViewOption: Option[(Evaluation, AutoView.Config) => AutoView[Evaluation]] = Some(evaluationView)

  def selectionView (init: Selection , config: AutoView.Config): AutoView[Selection ] =
    AutoView(init, config)

  def breedingView  (init: Breeding  , config: AutoView.Config): AutoView[Breeding  ] =
    AutoView(init, config)
}
