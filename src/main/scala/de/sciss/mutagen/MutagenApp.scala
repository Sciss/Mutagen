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

import java.awt.Color
import java.text.SimpleDateFormat
import java.util.{Date, Locale}

import com.alee.laf.WebLookAndFeel
import com.alee.laf.checkbox.WebCheckBoxStyle
import com.alee.laf.progressbar.WebProgressBarStyle
import de.sciss.audiowidgets.Transport
import de.sciss.desktop.impl.WindowImpl
import de.sciss.desktop.{KeyStrokes, OptionPane, DialogSource, FileDialog, Menu, Window, WindowHandler}
import de.sciss.file._
import de.sciss.lucre.swing.defer
import de.sciss.muta.gui.{DocumentFrame, GeneticApp}
import de.sciss.pdflitz
import de.sciss.processor.Processor
import de.sciss.synth.impl.DefaultUGenGraphBuilderFactory
import de.sciss.synth.swing.ServerStatusPanel
import de.sciss.synth.{Server, ServerConnection, Synth, SynthDef}
import scopt.OptionParser

import scala.swing.Swing._
import scala.swing.event.Key
import scala.swing.{Action, Button}
import scala.util.{Failure, Success, Try}

object MutagenApp extends GeneticApp(MutagenSystem) { app =>
  override protected def useNimbus         = false
  override protected def useInternalFrames = false

  final case class Options(in: Option[File] = None, auto: Boolean = false, autoSteps: Int = 50,
                           autoSeed: Boolean = false)

  private def parseArgs(): Options = {
    val parser  = new OptionParser[Options]("mutagen") {
      opt[Unit]('a', "auto") text "Auto run" action { (_, res) => res.copy(auto = true) }
      opt[File]('f', "file") text "JSON file to open" action { (arg, res) => res.copy(in = Some(arg)) }
      opt[Int]('n', "num-steps") text "Number of iterations between saving in auto run (default 50)" action {
        (arg, res) => res.copy(autoSteps = arg) }
      opt[Unit]('s', "seed") text "Use changing random seeds in auto run" action {
        (_, res) => res.copy(autoSeed = true) }
    }

    parser.parse(args, Options()).fold(sys.exit(1))(identity)
  }

  val opt = parseArgs()

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

    val Some(mExport: Menu.Group) = menuFactory.get("file.export")
    mExport.add(
      Menu.Item("audio-file", "Audio File..." -> (KeyStrokes.menu1 + Key.B))
    )

    new MainFrame

    opt.in.foreach { f =>
      val frOpt = openDocument(f)
      if (opt.auto) frOpt.foreach { fr =>
        val fileFormat = new SimpleDateFormat(s"'${f.base}'-yyMMdd'_'HHmmss'.json'", Locale.US)
        import scala.concurrent.ExecutionContext.Implicits.global
        def iter(): Unit = {
          if (opt.autoSeed) {
            val globOld  = fr.generation.global
            val seed     = util.Random.nextInt() // based on date
            val globNew  = globOld.copy(seed = seed)
            fr.generation = fr.generation.copy(global = globNew)
          }
          fr.iterate(n = opt.autoSteps, quiet = true).onComplete {
            case Success(_) =>
              println("Backing up...")
              import scala.sys.process._
              val child  = fileFormat.format(new Date(f.lastModified()))
              val out    = f.parentOption.fold(file(child))(_ / child)
              Seq("mv", f.path, out.path).!
              println("Saving...")
              fr.save(f).foreach { _ =>
                iter()
              }

            case Failure(Processor.Aborted()) => // then stop

            case Failure(ex) =>
              ex.printStackTrace()
              import scala.sys.process._
              Console.err.println("Restarting...")
              Seq("/bin/sh", "mutagen-auto").run()
              sys.exit()
          }
        }

        iter()
      }
    }
  }

  override protected def configureDocumentFrame(frame: DocumentFrame[MutagenSystem.type]): Unit = {
    var synthOpt = Option.empty[Synth]

    import de.sciss.synth.Ops._

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

    val butView = Button("View") {
      frame.selectedNodes.foreach { node =>
        val p = impl.SynthGraphViewImpl(node.chromosome.top)
        new WindowImpl { me =>
          def handler: WindowHandler = app.windowHandler

          private def source = {
            val dim = p.component.size
            pdflitz.Generate.QuickDraw(dim) { g2 =>
              val d = p.display
              d.damageReport()  // otherwise `paintDisplay` will be a no-op!
              d.paintDisplay(g2, dim)
            }
          }

          bindMenu("file.export.table", new pdflitz.SaveAction(source :: Nil))

          title     = node.chromosome.hashCode.toHexString
          contents  = p.component
          size      = (400, 400)
          front()
        }
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
    tp += butView
    tp += butStats
    tp += bs

    frame.bindMenu("file.export.audio-file", Action(null) {
      frame.selectedNodes.headOption.foreach { node =>
        val initFile = frame.file.map { f =>
          val d = f.parentOption
          val c = s"${f.base}-${node.chromosome.hashCode.toHexString}.aif"
          d.fold(file(c))(_ / c)
        }
        FileDialog.save(init = initFile, title = "Export Audio File").show(Some(frame.window)).foreach { f =>
          import scala.concurrent.ExecutionContext.Implicits.{global => exec}
          implicit val glob = frame.generation.global
          Evaluation.getInputSpec(node.chromosome).foreach { case (inputExtr, inputSpec) =>
            defer {
              val initial = f"${inputSpec.numFrames / inputSpec.sampleRate}%1.3f"
              val opt = OptionPane.textInput(message = "Duration [sec]:", initial = initial)
              opt.show(Some(frame.window)).foreach { durStr =>
                val proc = Evaluation.bounce(node.chromosome, f, duration = durStr.toDouble)
                proc.onFailure {
                  case ex => DialogSource.Exception(ex -> "Bounce").show(Some(frame.window))
                }
              }
            }
          }
        }
      }
    })
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
