package org.kumetix.enrichmentool.client.useragent

import java.net.URLEncoder

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.unmarshalling.{Unmarshal, Unmarshaller}
import akka.stream._

import scala.concurrent.{ExecutionContext, Future}

class PersistentCachingUserAgentClient[T](apiKey: String)
                                         (implicit system: ActorSystem,
                                          mat: Materializer) extends UserAgentClient[T] with JsonSupport {

  override def get(k: String)(implicit ec: ExecutionContext, unmarshaller: Unmarshaller[HttpResponse,T]): Future[T] = {
    Http().
      singleRequest(HttpRequest(uri = s"http://useragentapi.com/api/v4/json/$apiKey/${URLEncoder.encode(k,"UTF-8")}")).
      flatMap(Unmarshal(_).to[T])
  }
}
