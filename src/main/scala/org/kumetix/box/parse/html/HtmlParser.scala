package org.kumetix.box.parse.html

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.select.Elements

import scala.collection.mutable.ArrayBuffer

object HtmlParser {
  def parse(html:String): Document = {
    Jsoup.parse(html)
  }

  object Implicits {
    implicit class RichDocument(doc: Document) {
      def links: List[String] = {
        val links: Elements = doc.select("a[href]") // a with href
        val result = ArrayBuffer[String]()
        val itr = links.iterator()
        while (itr.hasNext) {
          result.append(itr.next().attr("href"))
        }
        result.toList
      }
      def images: List[String] = {
        import scala.collection.JavaConverters._
        val elements: Elements = doc.select("[src]")
        elements.asScala
          .filter(_.tagName().equals("img"))
          .map(_.attr("abs:src"))
          .toList
      }
    }
  }
}
