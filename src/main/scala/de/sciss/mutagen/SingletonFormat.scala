package de.sciss.mutagen

import play.api.libs.json.{JsSuccess, JsNull, JsResult, JsValue, Format}

class SingletonFormat[A](instance: A) extends Format[A] {
  def writes(instance: A): JsValue = JsNull

  def reads(json: JsValue): JsResult[A] = JsSuccess(instance)
}
