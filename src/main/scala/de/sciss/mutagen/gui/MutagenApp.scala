/*
 *  MutagenApp.scala
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

package de.sciss.mutagen.gui

import com.alee.laf.WebLookAndFeel
import de.sciss.audiowidgets.Transport
import de.sciss.desktop.{Window, WindowHandler}
import de.sciss.desktop.impl.WindowImpl
import de.sciss.muta.gui.{DocumentFrame, GeneticApp}
import de.sciss.mutagen.MutagenSystem
import de.sciss.synth
import de.sciss.synth.impl.DefaultUGenGraphBuilderFactory
import de.sciss.synth.{ServerConnection, SynthDef, ugen, Server, Synth}
import de.sciss.synth.swing.ServerStatusPanel

import scala.swing.{Button, Label, Swing}
import Swing._
import scala.util.Try

object MutagenApp extends GeneticApp(MutagenSystem) {
  override protected def useNimbus         = false
  override protected def useInternalFrames = false

  protected override def init(): Unit = {
    // println(MutagenSystem.chromosomeClassTag)
    WebLookAndFeel.install()
    super.init()

    new MainFrame
  }

  override protected def configureDocumentFrame(frame: DocumentFrame[MutagenSystem.type]): Unit = {
    var synthOpt = Option.empty[Synth]

    import synth.Ops._

    def stopSynth(): Unit = synthOpt.foreach { synth =>
      synthOpt = None
      synth.free()
    }
    def playSynth(): Unit = {
      stopSynth()
      for {
        s    <- Try(Server.default).toOption
        node <- frame.selectedNodes.headOption
      } {
        val graph = node.chromosome.graph
        val df    = SynthDef("test", graph.expand(DefaultUGenGraphBuilderFactory))
        val x     = df.play(s)
        synthOpt = Some(x)
      }
    }

    val pStatus = new ServerStatusPanel

    def boot(): Unit = {
      val cfg = Server.Config()
      cfg.pickPort()
      val connect = Server.boot(config = cfg) {
        case ServerConnection.Running(s) =>
        case ServerConnection.Aborted    =>
      }
      pStatus.booting = Some(connect)
    }

    val butKill = Button("Kill") {
      import scala.sys.process._
      "killall scsynth".!
    }

    pStatus.bootAction = Some(boot)
    val bs = Transport.makeButtonStrip(Seq(Transport.Stop(stopSynth()), Transport.Play(playSynth())))
    frame.topPanel.contents += pStatus
    frame.topPanel.contents += butKill
    frame.topPanel.contents += bs
  }

  private final class MainFrame extends WindowImpl {
    def handler: WindowHandler = MutagenApp.windowHandler

    title           = "Mutagen"
    resizable       = false
    closeOperation  = Window.CloseExit
    size            = (256, 256)
    front()
  }
}
