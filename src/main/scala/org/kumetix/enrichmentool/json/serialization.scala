package org.kumetix.enrichmentool.json
import org.kumetix.enrichmentool.dto.useragentdata.{Data, UserAgentData}
import spray.json._
import DefaultJsonProtocol._

object serialization {
  implicit val data = jsonFormat4(Data.apply)
  implicit val userAgentData = jsonFormat1(UserAgentData.apply)
}
