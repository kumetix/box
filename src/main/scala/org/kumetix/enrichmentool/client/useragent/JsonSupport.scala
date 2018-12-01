package org.kumetix.enrichmentool.client.useragent

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import org.kumetix.enrichmentool.client.{Data, UserAgentData}
import spray.json.DefaultJsonProtocol

trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol  {
  // internal struct
  implicit val dataFormat = jsonFormat3(Data)
  // api
  implicit val userAgentDataFormat = jsonFormat1(UserAgentData)
}
