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
import de.sciss.guiflitz.{AutoView, Cell}
import de.sciss.model.Model
import de.sciss.play.json.AutoFormat
import de.sciss.synth.io.AudioFile
import de.sciss.{muta, mutagen}
import play.api.libs.json.{Format, JsError, JsResult, JsString, JsSuccess, JsValue}

import scala.reflect.{ClassTag, classTag}
import scala.swing.Label
import scala.swing.event.ValueChanged

object MutagenSystem extends muta.System {
  // WARNING: must not be `implicit` to avoid false recursion
  val chromosomeClassTag: ClassTag[Chromosome] = classTag[mutagen.Chromosome]

  // ---- settings ----

  type Global     = mutagen.Global

  override def normalizeFitness = false

  // ---- types ----

  type Chromosome = mutagen.Chromosome
  type Generation = mutagen.Generation
  type Evaluation = mutagen.Evaluation

  type Selection  = mutagen.Selection

  type Breeding = mutagen.Breeding

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

  val evaluationFormat: Format[Evaluation] = AutoFormat[Evaluation]
  val selectionFormat : Format[Selection ] = AutoFormat[Selection]
  val breedingFormat  : Format[Breeding  ] = AutoFormat[Breeding]

  // ---- instances ----

  def defaultGeneration: Generation = Generation()

  def defaultEvaluation: Evaluation = Evaluation()
  def defaultSelection : Selection  = Selection()
  def defaultBreeding  : Breeding   = Breeding()

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
