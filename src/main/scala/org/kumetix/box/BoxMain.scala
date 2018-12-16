package org.kumetix.box

import com.typesafe.scalalogging.LazyLogging
import org.kumetix.box.internet.Crawler

object BoxMain extends App with LazyLogging {

  println("       _____     ")
  println("Hello |     |    ")
  println("      |_____| box")

  val crawler = new Crawler

  val root = "https://www.ynet.co.il"

  val maxResourcesToFetch = 100000000
  val amountOfCacheOrFetchWorkers = 128

  logger info s"STARTING extraction of maximum $maxResourcesToFetch resources, rooting from $root"
  val seq = crawler.crawl(root,maxResourcesToFetch,amountOfCacheOrFetchWorkers)
  logger info s"FINISHED extraction ; ${seq.length} resources in memory"
  sys.exit()

}