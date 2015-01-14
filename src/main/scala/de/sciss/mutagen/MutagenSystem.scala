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
import de.sciss.muta.{SelectionNumber, BreedingFunction, SelectionPercent, SelectionSize, Vec}
import de.sciss.mutagen.gui.PathField
import de.sciss.play.json.AutoFormat
import de.sciss.synth.io.AudioFile
import de.sciss.{muta, mutagen}
import play.api.libs.json.{JsSuccess, JsError, JsString, JsResult, JsValue, Format}

import scala.reflect.{ClassTag, classTag}
import scala.swing.Label
import scala.swing.event.ValueChanged
import scala.util.Random

object MutagenSystem extends muta.System {
  // WARNING: must not be `implicit` to avoid false recursion
  val chromosomeClassTag: ClassTag[Chromosome] = classTag[mutagen.Chromosome]

  // ---- settings ----

  type Global = mutagen.Global

  // ---- types ----

  type Chromosome = mutagen.Chromosome

  case class Generation(global: Global = Global()) extends muta.Generation[Chromosome, Global] {
    def size: Int = global.population
    def seed: Int = global.seed

    def apply(random: Random): Chromosome = Chromosome()(random, global)
  }

  type Evaluation = mutagen.Evaluation

  object Selection extends muta.Selection[Chromosome] with muta.impl.SelectionRouletteImpl[Chromosome] {

    // def apply(eval: Vec[(Chromosome, Double)], rnd: Random): Vec[Chromosome] = ...
    def size: SelectionSize = SelectionPercent(25)
  }
  type Selection = Selection.type

  object Breeding extends muta.Breeding[Chromosome, Global] with muta.impl.BreedingImpl[Chromosome, Global] {
    //    def apply(sel: Vec[(Chromosome, Double, Boolean)], g: Global, rnd: Random): Vec[Chromosome] = {
    //      Vec.tabulate(g.population)(i => sel(i % sel.size)._1)
    //    }

    val elitism        : SelectionSize    = SelectionNumber (4)  // XXX TODO
    val crossoverWeight: SelectionPercent = SelectionPercent(0)  // XXX TODO

    object crossover extends BreedingFunction[Chromosome, Global] {
      def apply(genome: Vec[Chromosome], sz: Int, global: Global, rnd: Random): Vec[Chromosome] = {
        ???
      }
    }

    def mutation: BreedingFunction[Chromosome, Global] = impl.MutationImpl
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

  val evaluationFormat: Format[Evaluation] = AutoFormat[Evaluation]
  val selectionFormat : Format[Selection ] = new SingletonFormat(Selection )
  val breedingFormat  : Format[Breeding  ] = new SingletonFormat(Breeding  )

  // ---- instances ----

  def defaultGeneration: Generation = Generation()

  def defaultEvaluation: Evaluation = Evaluation()
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
