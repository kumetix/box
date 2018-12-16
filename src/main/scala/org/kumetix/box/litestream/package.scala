package org.kumetix.box

import org.kumetix.box.litestream.Producer

package object litestream {

  sealed trait StreamResult
  case class StreamSuccess[T](o:T) extends StreamResult
  case class StreamFailure(t:Throwable) extends StreamResult

  trait Producer[A] {
    def produce: Option[A]
  }

  trait Consumer[A,B] {
    def consume(a:A): B
  }

  trait Flow[A,B] extends Consumer[A,B] with Producer[B]

  trait Sink[A] extends Consumer[A,StreamResult]{
    def consume(o: A): StreamResult
  }

  object dsl {

    import scala.collection.mutable.ListBuffer

    class Builder {
      var stream = ListBuffer[Any]()
      def connect[A,B](producer: Producer[A], consumer: Consumer[A,B]) = {
        stream.append(producer,consumer)
      }
      def connect[A,B](producer: Producer[A], sink: Sink[B]) = {
        stream.append(producer,sink)
      }
    }

    implicit class RichProducer[A](val p: Producer[A]) extends AnyVal {
      def via[B](parallelism: Int)(c: Consumer[A,B])(implicit builder: Builder) = {
        builder.connect(p,c)
        c
      }
      def into(s: Sink[A])(implicit builder: Builder) = {
        builder.connect(p,s)
        s
      }
    }
  }
}

object LiteStreamMain extends App {
  println("hello litestream")

  import scala.io.{Source => IOSource}

  class FromResourceFileProducer(r: String) extends Producer[String] {
    val file = IOSource.fromResource(r)
    val iterator = file.getLines()
    override def produce: Option[String] = if (iterator.hasNext) Some(iterator.next()) else None
  }

  val lineSource = new FromResourceFileProducer("logback.xml")

}