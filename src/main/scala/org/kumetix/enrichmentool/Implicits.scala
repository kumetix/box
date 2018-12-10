package org.kumetix.enrichmentool

import spray.json._
import DefaultJsonProtocol._

object Implicits {
  implicit class RichString(s: String) {
    def as[T](implicit reader: JsonReader[T]) = s.parseJson.convertTo[T]
  }
  implicit class RichCaseClass[T](val o: T) extends AnyVal {
    def json(implicit writer: JsonWriter[T]) = o.toJson(writer)
  }
}
