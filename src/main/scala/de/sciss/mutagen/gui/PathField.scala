/*
 *  PathField.scala
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

import javax.swing.{JComponent, JPanel}

import de.sciss.desktop.FileDialog
import de.sciss.file._

import scala.swing.event.{ValueChanged, EditDone}
import scala.swing.{Button, TextField, Component}

class PathField
  extends Component {

  private var _value: File = _

  var title = "Open File"
  var accept: File => Option[File] = Some(_)

  def value: File = _value
  /** Does not fire */
  def value_=(f: File): Unit = {
    _value = f
    tx.text = f.path
  }

  /** Treats empty file as `None` */
  def valueOption: Option[File] = if (_value.path == "") None else Some(_value)
  def valueOption_=(opt: Option[File]): Unit = value = opt.getOrElse(file(""))

  private def setValue(newValue: File): Unit =
    if (newValue != _value) {
      value = newValue
      publish(new ValueChanged(this))
    }

  private lazy val tx = new TextField(16)
  tx.listenTo(tx)
  tx.reactions += {
    case EditDone(_) =>
      setValue(new File(tx.text))
  }
  private lazy val bt = Button("…") {
    val dlg = FileDialog.open(init = valueOption, title = title)
    dlg.show(None).flatMap(accept).foreach(setValue)
  }

  valueOption = None
  bt.tooltip = "Show File Chooser"

  override lazy val peer: JComponent =
    new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.TRAILING, 0, 0)) with SuperMixin {
      override def getBaseline(width: Int, height: Int): Int = {
        val res = tx.peer.getBaseline(width, height)
        res + tx.peer.getY
      }

      add(tx.peer)
      add(bt.peer)
    }
}
