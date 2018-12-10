package org.kumetix.enrichmentool

import java.io.{BufferedWriter, Closeable, File, FileWriter}

import akka.actor.ActorSystem
import akka.http.scaladsl.unmarshalling.PredefinedFromEntityUnmarshallers
import akka.stream.ActorMaterializer
import com.redis.RedisClient
import com.typesafe.scalalogging.Logger
import org.kumetix.enrichmentool.Utils._
import org.kumetix.enrichmentool.client.useragent.PersistentCachingUserAgentClient
import spray.json.JsObject
import spray.json._
import DefaultJsonProtocol._
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration._
import scala.concurrent.{Await, Future, TimeoutException}
import scala.collection.JavaConverters._

case class Args(apiKey: String, input: String, output: String, redisHost: String, redisPort: String)
object Args {
  def apply: Args = {
    val PROP_API_KEY = "org.kumetix.enrichmentool.apikey"
    val PROP_INPUT = "org.kumetix.enrichmentool.input"
    val PROP_OUTPUT = "org.kumetix.enrichmentool.output"
    val PROP_REDIS_HOST = "org.kumetix.enrichmentool.redisHost"
    val PROP_REDIS_PORT = "org.kumetix.enrichmentool.redisPort"
    val MANDATORY_PROPS = Set(PROP_API_KEY,PROP_INPUT,PROP_OUTPUT,PROP_REDIS_HOST,PROP_REDIS_PORT)
    val props = System.getProperties.keySet().asScala.map(_.asInstanceOf[String]).toSet
    val missing = MANDATORY_PROPS -- props
    if (missing.nonEmpty) {
      throw new RuntimeException(s"missing system property(ies): [${missing.mkString(",")}]")
    }
    else {
      Args(
        apiKey = System.getProperty(PROP_API_KEY),
        input = System.getProperty(PROP_INPUT),
        output = System.getProperty(PROP_OUTPUT),
        redisHost = System.getProperty(PROP_REDIS_HOST),
        redisPort = System.getProperty(PROP_REDIS_PORT)
      )
    }
  }
}

object Enrichmentool extends App with PredefinedFromEntityUnmarshallers {
  implicit val system = ActorSystem()
  implicit val mat = ActorMaterializer()
  implicit val ec = system.dispatcher

  val logger = Logger(Enrichmentool.getClass)

  val appArgs = Args.apply

  val redisClient =
    new RedisClient(appArgs.redisHost, appArgs.redisPort.toInt)

  val userAgentClient =
    new PersistentCachingUserAgentClient[JsObject](apiKey = appArgs.apiKey, redisClient)

  val inputPath = appArgs.input
  val outputFile = new File(appArgs.output)

  logger info s"starting enrichment of input '$inputPath' , result will be emitted to '${outputFile.getAbsoluteFile}'"

  val synchronizedOutputWriter = new BufferedWriter(new FileWriter(outputFile)) {
    override def write(str: String): Unit = synchronized {
      logger debug s"writing [$str] into ${outputFile.getAbsoluteFile}"
      super.write(s"$str\n")
    }
  }

  val futures = ArrayBuffer[Future[String]]()
  using(EntrySource.fromFile(new File(inputPath))) { itr =>
    while (itr.hasNext) {
      val (idx, csv) = itr.next()
      logger info s"handling line $idx"
      val f: Future[String] = for {
        data <- userAgentClient.get(csv("userAgent")).map(_.fields("data").asJsObject)
        newCsv = csv ++ data.fields.mapValues(_.toString())
      } yield {
        val line = if (idx == 0) newCsv.keys.mkString(",") else newCsv.values.mkString(",")
        line
      }
      futures.append(f)
    }
  }

  try {
    Await.ready(Future.sequence(futures map {_.map(synchronizedOutputWriter.write)}), 20 seconds)
    logger info s"enrichment completed successfully, checkout content of ${outputFile.getAbsoluteFile}"
    synchronizedOutputWriter.close()
    sys.exit()
  } catch {
    case _:TimeoutException => logger error "enrichment timed out"
    case t:Throwable => logger error ("enrichment failed",t)
  }

}

object EntrySource {
  type CSV = (Int,Map[String,String])
  def fromFile(f:File): Iterator[CSV] with Closeable = {
    var idx = -1
    val source = io.Source.fromFile(f)
    val innerItr = source.getLines()
    val optHeaders = if (innerItr.hasNext) Some(innerItr.next()) else None
    if (optHeaders.isEmpty) {
      logger info s"empty input file"
      emptyIterator
    }
    else {
      logger info s"non-empty input file"
      val headers: Array[String] = optHeaders.get.csv.map(_.replaceAll("\"",""))
      new Iterator[CSV] with Closeable {
        override def hasNext: Boolean = innerItr.hasNext
        override def next(): CSV =  {
          idx = idx + 1
          idx -> headers.zip(innerItr.next().csv).toMap
        }
        override def close(): Unit = source.close()
      }
    }
  }

  private implicit class RichString(s: String) {
    def csv = s.split(",").map(_.trim)
  }

  private val emptyIterator = new Iterator[CSV] with Closeable {
    override def hasNext: Boolean = false
    override def next(): (Int, Map[String, String]) = ???
    override def close(): Unit = {}
  }

  val logger = Logger(Enrichmentool.getClass)
}