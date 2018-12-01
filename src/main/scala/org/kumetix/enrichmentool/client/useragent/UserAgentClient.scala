package org.kumetix.enrichmentool.client.useragent

import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.unmarshalling.Unmarshaller

import scala.concurrent.{ExecutionContext, Future}

trait UserAgentClient[T] {

  def get(k: String)(implicit ec: ExecutionContext,
                     unmarshaller: Unmarshaller[HttpResponse,T]): Future[T]

}
