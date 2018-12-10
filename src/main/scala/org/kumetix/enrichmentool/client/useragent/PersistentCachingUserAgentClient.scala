package org.kumetix.enrichmentool.client.useragent

import java.net.URLEncoder

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.unmarshalling.{FromResponseUnmarshaller, PredefinedFromEntityUnmarshallers, Unmarshal, Unmarshaller}
import akka.stream._
import akka.util.ByteString
import com.redis.RedisClient
import com.redis.serialization.Parse
import com.typesafe.scalalogging.Logger
import org.kumetix.enrichmentool.Implicits._
import spray.json.{JsonFormat, _}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

object PersistentCachingUserAgentClient

class PersistentCachingUserAgentClient[T: JsonFormat](apiKey: String, redisClient: RedisClient)
                                                     (implicit system: ActorSystem,
                                                      mat: Materializer)
  extends UserAgentClient[T] with PredefinedFromEntityUnmarshallers {

  implicit val ec = system.dispatcher

  val logger = Logger(PersistentCachingUserAgentClient.getClass)

  // http response => json => T

  // byte array from redis => json string => T
  implicit val redisParse = Parse[T] { case bytes =>
    bytes.map(_.toChar).mkString.as[T]
  }

  override def get(k: String): Future[T] = {
    redisClient.get[T](k) match {
      case Some(v) =>
        logger info s"fetched $k from cache"
        Future.successful(v)
      case None =>
        Http()
          .singleRequest(HttpRequest(uri = s"http://useragentapi.com/api/v4/json/$apiKey/${URLEncoder.encode(k, "UTF-8")}"))
          .flatMap(_.entity.toStrict(3 seconds)
            .flatMap{ e =>
              e.dataBytes
                .runFold(ByteString.empty)(_++_)
                .map { v =>
                  redisClient.set(k, v.utf8String)
                  logger info s"fetched $k from web"
                  val json = v.utf8String.parseJson
                  json.convertTo[T]
                }
            })
    }
  }
}
