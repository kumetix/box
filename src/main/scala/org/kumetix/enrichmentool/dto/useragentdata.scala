package org.kumetix.enrichmentool.dto

object useragentdata {
  // api
  case class UserAgentData(data: Data)

  // internal struct
  case class Data(ua_type: String, ua_brand: String, ua_name: String)
}
