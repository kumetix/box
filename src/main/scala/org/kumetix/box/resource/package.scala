package org.kumetix.box

package object resource {
  // a fetchable thing
  trait Resource {
    self =>
    def imageLinks(): Seq[String]
    def links(): Seq[String]
    def `type`: String = self.getClass.getSimpleName
    override def toString: String = s"{${`type`} ; images:${imageLinks().size} ; links:${links().size} }"
  }

  // HTML pages can link to Imgs and to other HTML pages.
  class HTMLPage(val i: Seq[String], val l: Seq[String]) extends Resource {
    def imageLinks() = i
    def links = l
  }

  // IMGs don't actually link to anything else
  class Img() extends Resource {
    def imageLinks() = Seq()
    def links() = Seq()
  }
}
