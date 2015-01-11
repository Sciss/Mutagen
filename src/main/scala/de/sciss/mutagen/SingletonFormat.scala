/*
 *  SingletonFormat.scala
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

import play.api.libs.json.{JsSuccess, JsNull, JsResult, JsValue, Format}

class SingletonFormat[A](instance: A) extends Format[A] {
  def writes(instance: A): JsValue = JsNull

  def reads(json: JsValue): JsResult[A] = JsSuccess(instance)
}
