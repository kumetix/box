package org.kumetix.enrichmentool.client.useragent

import scala.concurrent.Future

trait UserAgentClient[T] {

  def get(k: String): Future[T]

}
