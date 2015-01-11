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
import de.sciss.desktop.{Window, WindowHandler}
import de.sciss.desktop.impl.WindowImpl
import de.sciss.muta.gui.GeneticApp
import de.sciss.mutagen.MutagenSystem

import scala.swing.Swing
import Swing._

object MutagenApp extends GeneticApp(MutagenSystem) {
  override protected def useNimbus         = false
  override protected def useInternalFrames = false

  protected override def init(): Unit = {
    // println(MutagenSystem.chromosomeClassTag)
    WebLookAndFeel.install()
    super.init()

    new MainFrame
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
