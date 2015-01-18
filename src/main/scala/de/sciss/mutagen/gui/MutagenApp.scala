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

package de.sciss.mutagen
package gui

import java.awt.Color

import com.alee.laf.WebLookAndFeel
import com.alee.laf.checkbox.WebCheckBoxStyle
import com.alee.laf.progressbar.WebProgressBarStyle
import de.sciss.audiowidgets.Transport
import de.sciss.desktop.{Window, WindowHandler}
import de.sciss.desktop.impl.WindowImpl
import de.sciss.file._
import de.sciss.muta.gui.{DocumentFrame, GeneticApp}
import de.sciss.synth
import de.sciss.synth.impl.DefaultUGenGraphBuilderFactory
import de.sciss.synth.{ServerConnection, SynthDef, Server, Synth}
import de.sciss.synth.swing.ServerStatusPanel

import scala.concurrent.ExecutionContext
import scala.swing.{Button, Swing}
import Swing._
import scala.util.{Success, Failure, Try}

object MutagenApp extends GeneticApp(MutagenSystem) {
  override protected def useNimbus         = false
  override protected def useInternalFrames = false

  protected override def init(): Unit = {
    // println(MutagenSystem.chromosomeClassTag)
    WebLookAndFeel.install()
    // some custom web-laf settings
    WebCheckBoxStyle   .animated            = false
    WebProgressBarStyle.progressTopColor    = Color.lightGray
    WebProgressBarStyle.progressBottomColor = Color.gray
    // XXX TODO: how to really turn of animation?
    WebProgressBarStyle.highlightWhite      = new Color(255, 255, 255, 0)
    WebProgressBarStyle.highlightDarkWhite  = new Color(255, 255, 255, 0)

    super.init()

    new MainFrame

    args.toList match {
      case "--auto" :: path :: _ =>
        val f = file(path)
        openDocument(f).foreach { fr =>
          import ExecutionContext.Implicits.global
          def iter(): Unit = {
            fr.iterate(n = 50, quiet = true).onComplete {
              case Success(_) =>
                println("Saving...")
                fr.save(f).foreach { _ =>
                  iter()
                }

              case Failure(ex) =>
                ex.printStackTrace()
                import sys.process._
                Console.err.println("Restarting...")
                Seq("/bin/sh", "mutagen-auto").run()
                sys.exit()
            }
          }

          iter()
        }

      case _ =>
    }
  }

  override protected def configureDocumentFrame(frame: DocumentFrame[MutagenSystem.type]): Unit = {
    var synthOpt = Option.empty[Synth]

    import synth.Ops._

    def stopSynth(): Unit = synthOpt.foreach { synth =>
      synthOpt = None
      if (synth.server.isRunning) synth.free()
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
      Try(Server.default).toOption.foreach(_.dispose())
      "killall scsynth".!
    }

    val butPrint = Button("Print") {
      frame.selectedNodes.foreach { node =>
        val txt = node.chromosome.graphAsString
        println(txt)
      }
    }

    val butStats = Button("Stats") {
      impl.MutationImpl.printStats()
    }

    pStatus.bootAction = Some(boot)
    val bs = Transport.makeButtonStrip(Seq(Transport.Stop(stopSynth()), Transport.Play(playSynth())))
    val tp = frame.topPanel.contents
    tp += pStatus
    tp += butKill
    tp += butPrint
    tp += butStats
    tp += bs
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
