package de.sciss.mutagen
package gui

import de.sciss.guiflitz.AutoView
import de.sciss.muta
import de.sciss.muta.Vec
import de.sciss.mutagen
import play.api.libs.json.{JsResult, JsValue, Format}

import scala.reflect.{ClassTag, classTag}
import scala.util.Random

object MutagenSystem extends muta.System {
  // ---- settings ----

  type Global     = Unit

  // ---- types ----

  type Chromosome = mutagen.Chromosome

  case class Generation(size: Int, seed: Int) extends muta.Generation[Chromosome, Global] {
    def global: Global = ()

    def apply(rnd: Random): Chromosome = ???
  }

  object Evaluation extends muta.Evaluation[Chromosome] {
    def apply(c: Chromosome): Double = ???
  }
  type Evaluation = Evaluation.type

  object Selection extends muta.Selection[Chromosome] {
    def apply(eval: Vec[(Chromosome, Double)], rnd: Random): Vec[Chromosome] = ???
  }
  type Selection = Selection.type

  object Breeding extends muta.Breeding[Chromosome, Global] {
    def apply(sel: Vec[(Chromosome, Double, Boolean)], g: Global, rnd: Random): Vec[Chromosome] = ???
  }
  type Breeding = Breeding.type

  // ---- serialization ----

  def chromosomeFormat: Format[Chromosome] = ???

  def generationFormat: Format[Generation] = ???

  val evaluationFormat: Format[Evaluation] = new SingletonFormat(Evaluation)

  def selectionFormat : Format[Selection ] = ???

  def breedingFormat  : Format[Breeding  ] = ???

  // ---- instances ----

  def defaultGeneration: Generation = ???

  def defaultEvaluation: Evaluation = ???

  def defaultSelection : Selection = ???

  def defaultBreeding  : Breeding = ???

  // ---- views ----

  def generationView(init: Generation, config: AutoView.Config): AutoView[Generation] = ???

  def evaluationViewOption: Option[(Evaluation, AutoView.Config) => AutoView[Evaluation]] = None

  def selectionView (init: Selection , config: AutoView.Config): AutoView[Selection] = ???

  def breedingView  (init: Breeding  , config: AutoView.Config): AutoView[Breeding] = ???

  // ----

  implicit val chromosomeClassTag: ClassTag[Chromosome] = classTag[Chromosome]
}
