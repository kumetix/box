package org.kumetix.enrichmentool

package object model {
  case class UserAgentData(name: String,
                           `type`: String,
                           brand: String,
                           osName: String,
                           browserName: String)
}
