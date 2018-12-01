package org.kumetix.enrichmentool

import akka.actor.ActorSystem
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.unmarshalling.{PredefinedFromEntityUnmarshallers, Unmarshal, Unmarshaller}
import akka.stream.{ActorMaterializer, Materializer}
import org.kumetix.enrichmentool.client.UserAgentData
import org.kumetix.enrichmentool.client.useragent.{JsonSupport, PersistentCachingUserAgentClient}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

// 05be3daf

object Enrichmentool extends App with PredefinedFromEntityUnmarshallers with JsonSupport {
  implicit val system = ActorSystem()
  implicit val mat = ActorMaterializer()
  implicit val ec = system.dispatcher

  implicit val unmarshaller = new Unmarshaller[HttpResponse,UserAgentData] {
    override def apply(value: HttpResponse)
                      (implicit ec: ExecutionContext,
                       materializer: Materializer): Future[UserAgentData] = {
      Unmarshal(value.entity).to[UserAgentData]
    }
  }

  val userAgentClient = new PersistentCachingUserAgentClient[UserAgentData](apiKey = "05be3daf")

  val res = Await.result(userAgentClient.get("Xbox Live Client/2.0.17511.0"), 5.seconds)
  println(res)
}
