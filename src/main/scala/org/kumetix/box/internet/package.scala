package org.kumetix.box

import com.typesafe.scalalogging.LazyLogging
import org.kumetix.box.concurrent._
import org.kumetix.box.parse.html.HtmlParser
import org.kumetix.box.resource.{HTMLPage, Img, Resource}

import scala.collection.concurrent.TrieMap
import scala.collection.immutable
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration.{Duration, _}
import scala.util.Try

package object internet {

  trait BlockingInternet {
    def syncInternet(url: String): Try[Resource]
  }

  trait FakeBlockingInternet extends BlockingInternet  {
    // profile.html links to gallery.html and has an image link to portrait.jpg
    val profile = new HTMLPage(Seq("portrait.jpg"), Seq("gallery.html"))
    val portrait = new Img

    // gallery.html links to profile.html and two images
    val gallery = new HTMLPage(Seq("kitten.jpg", "puppy.jpg"), Seq("profile.html"))
    val kitten = new Img
    val puppy = new Img

    private val fakeInternetMap = Map(
      "profile.html" -> profile,
      "gallery.html" -> gallery,
      "portrait.jpg" -> portrait,
      "kitten.jpg" -> kitten,
      "puppy.jpg" -> puppy)
    //def asyncInternet(k: String) = Future(fakeInternetMap(k))
  }

  trait RealBlockingInternet extends LazyLogging with BlockingInternet {
    import HtmlParser.Implicits._
    override def syncInternet(url: String): Try[Resource] = {
      logger debug s"loading from web $url"
      Try {
        val doc = HtmlParser.parse(get(url,5000,5000))
        new HTMLPage(doc.images, doc.links)
      }
    }
  }

  trait BlockingInMemoryCacheFetch extends LazyLogging with BlockingInternet {

    //import scala.collection.JavaConverters._
    def sharedCache =   new TrieMap[String, Try[Resource]]

    def cacheOrFetch(url: String): (Boolean,Try[Resource]) = {
      val maybeResource = sharedCache.get(url)
      if (maybeResource.isEmpty) {
        val resource = fetch(url)
        sharedCache.put(url,resource)
        true -> resource
      } else {
        false -> maybeResource.get
      }
    }


    private def fetch(url: String) = {
      val action = s"fetching & parsing $url"
      logger debug action
      val result = syncInternet(url)
      logger trace s"$action 'result': $result"
      result
    }
  }

  class Crawler extends LazyLogging {

    def crawl(initalUrl: String, amountToCollect: Int = 1000, cacheOrFetchWorkers: Int = 2048): Array[Resource] = {

      logger info s"crawling ; extracting $amountToCollect pages using $cacheOrFetchWorkers cacheOrFetch workers"

      // in-memory queues creation
      val urlQueue: MessageQueue[String] =
        createInMemoryQueue("UrlQueue", List(initalUrl))
      val cacheOrFetchQueue: MessageQueue[String] =
        createInMemoryQueue[String]("CacheOrFetchQueue", maxSize = 1000000)
      val resultsQueue: MessageQueue[Resource] =
        createInMemoryQueue[Resource]("ResultsQueue", maxSize = 1000)

      val statsPrinter =
        createStatsPrinterThread(10 seconds)(urlQueue :: cacheOrFetchQueue :: resultsQueue :: Nil)
      statsPrinter.start()

      logger info s"starting UrlConsumer"
      val urlConsumerThread = new StoppableConsumerThread(
        new UrlConsumer(urlQueue)(cacheOrFetchQueue), "UrlConsumer"
      )
      urlConsumerThread.start()

      logger info s"starting $cacheOrFetchWorkers CacheOrFetchWorker"
      val cacheOrFetchConsumerThreads: immutable.Seq[StoppableConsumerThread] = {
        val sharedWorkersCache = new TrieMap[String,Try[Resource]]()
        (1 to cacheOrFetchWorkers).map(i => {
          new StoppableConsumerThread(
            new CacheOrFetchWorker(
              cacheOrFetchQueue, resultsQueue, urlQueue, sharedWorkersCache
            ), s"CacheOrFetchWorker-$i")
        })
      }
      cacheOrFetchConsumerThreads
        .foreach(_.start())

      logger info s"starting ResultsConsumer"

      val resultsConsumerThread = new InMemoryConsumerThread(
        new IntoMemoryResultsConsumer(resultsQueue)(amountToCollect), "ResultsConsumer"
      )

      resultsConsumerThread.start()
      try {
        resultsConsumerThread.join()
        resultsConsumerThread.result
      } finally {
        cacheOrFetchConsumerThreads.foreach(_.safeStop())
        urlConsumerThread.safeStop()
        statsPrinter.safeStop()
      }

    }


    class UrlConsumer(override val inQueue: MessageQueue[String])
                     (outCacheOrFetchQueue: MessageQueue[String])
      extends StoppableRunnableConsumer[String] with LazyLogging with Runnable {
      override def onMessage(m: String): Unit = {
        super.onMessage(m)
        outCacheOrFetchQueue.push(m)
      }
    }

    class CacheOrFetchWorker(override val inQueue: MessageQueue[String],
                             outResultsQueue: MessageQueue[Resource],
                             outUrlQueue: MessageQueue[String],
                             override val sharedCache: TrieMap[String,Try[Resource]])
      extends StoppableRunnableConsumer[String]
        with BlockingInMemoryCacheFetch
        with RealBlockingInternet
        with Runnable
        with LazyLogging
    {
      override def onMessage(m: String): Unit = {
        super.onMessage(m)
        val (isFirstTime, triedResource) = cacheOrFetch(m)
        if (isFirstTime && triedResource.isSuccess) {
          outUrlQueue.push(triedResource.get.links())
          outResultsQueue.push(triedResource.get)
        }
//        if (triedResource.isFailure) {
//          logger warn s"message processing failure [MSG : $m] [FAILURE: ${triedResource.failed.get.getClass}]"
//        }
      }
    }

    class IntoMemoryResultsConsumer(override val inQueue: MessageQueue[Resource])(stopAt: Int = 0)
      extends StoppableRunnableConsumer[Resource] with LazyLogging with Runnable{
      var collected: ArrayBuffer[Resource] = collection.mutable.ArrayBuffer[Resource]()
      override def onMessage(m: Resource): Unit = {
        super.onMessage(m)
        collected += m
        val total = collected.size
        if (total % 100 == 0)
          logger info s"${collected.size} collected"
        if (stopAt > 0 && total >= stopAt) {
          logger info s"resultsConsumer have consumed $total resources"
          stop()
        }
      }
    }

    class InMemoryConsumerThread(runnable: IntoMemoryResultsConsumer,
                                 name: String) extends Thread(runnable,name) {
      def result = if (runnable.isStopped) {
        runnable.collected.toArray
      } else throw new IllegalStateException()
    }

    def createStatsPrinterThread(interval: Duration)(queues: List[MessageQueue[_]])  = {
      val Request = "PrintStats"
      new StoppableConsumerThread(
        new StoppableRunnableConsumer[String] with LazyLogging {
          override val inQueue: MessageQueue[String] = createInMemoryQueue("DumpStatsRequests", List(Request))
          override def onMessage(m: String): Unit = {
            super.onMessage(m)
            val statsString = queues
              .map(q => s"${q.name}: ${q.size}")
              .mkString("queues stats: [\n\t","\n\t","\n]")
            logger info statsString
            inQueue.push(Request)
            Thread.sleep(interval.toMillis)
          }
        }, "StatsPrinter")
    }
  }


  class Printer extends LazyLogging {
    def printResult(title: String, resources: Seq[Resource]) = {
      resources.foreach(r => logger info s"$title\t:\t${r.`type`}")
    }

    def printResult(title: String, r: Resource) = {
      logger info s"$title\t:\t${r.`type`}"
    }
  }




  /**
    * Returns the text (content) from a REST URL as a String.
    * Inspired by http://matthewkwong.blogspot.com/2009/09/scala-scalaiosource-fromurl-blockshangs.html
    * and http://alvinalexander.com/blog/post/java/how-open-url-read-contents-httpurl-connection-java
    *
    * The `connectTimeout` and `readTimeout` comes from the Java URLConnection
    * class Javadoc.
    * @param url The full URL to connect to.
    * @param connectTimeout Sets a specified timeout value, in milliseconds,
    * to be used when opening a communications link to the resource referenced
    * by this URLConnection. If the timeout expires before the connection can
    * be established, a java.net.SocketTimeoutException
    * is raised. A timeout of zero is interpreted as an infinite timeout.
    * Defaults to 5000 ms.
    * @param readTimeout If the timeout expires before there is data available
    * for read, a java.net.SocketTimeoutException is raised. A timeout of zero
    * is interpreted as an infinite timeout. Defaults to 5000 ms.
    * @param requestMethod Defaults to "GET". (Other methods have not been tested.)
    *
    * @example get("http://www.example.com/getInfo")
    * @example get("http://www.example.com/getInfo", 5000)
    * @example get("http://www.example.com/getInfo", 5000, 5000)
    */
  @throws(classOf[java.io.IOException])
  @throws(classOf[java.net.SocketTimeoutException])
  def get(url: String,
          connectTimeout: Int = 5000,
          readTimeout: Int = 5000,
          requestMethod: String = "GET") =
  {
    import java.net.{HttpURLConnection, URL}
    val connection = new URL(url).openConnection.asInstanceOf[HttpURLConnection]
    connection.setConnectTimeout(connectTimeout)
    connection.setReadTimeout(readTimeout)
    connection.setRequestMethod(requestMethod)
    val inputStream = connection.getInputStream
    val content = io.Source.fromInputStream(inputStream,"UTF-8").mkString
    if (inputStream != null) inputStream.close
    content
  }

  def createSet[T]() = {
    import scala.collection.JavaConverters._
    java.util.Collections.newSetFromMap(
      new java.util.concurrent.ConcurrentHashMap[T, java.lang.Boolean]).asScala
  }
}
