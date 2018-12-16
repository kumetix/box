package org.kumetix.box

import java.util.concurrent.{BlockingQueue, ConcurrentLinkedQueue, LinkedBlockingQueue}

import com.typesafe.scalalogging.LazyLogging

package object concurrent {

  // ToDo implement wrapper around rmq/sqs/kafka/etc.. that implements this and you're set
  // should we wish to out-source our queue, it is possible to simply implement wrappers around rmq/sqs/kafka that implement
  // this trait
  trait MessageQueue[T] {
    def name: String
    def isEmpty: Boolean
    def pop(): Option[T]
    def push(m:T): Unit
    def push(ms:Seq[T]): Unit
    def size: Int
  }

  trait MessageQueueConsumer[T] {
    def inQueue: MessageQueue[T]
  }

  class StoppableConsumerThread(stoppableRunnable: StoppableRunnableConsumer[_],
                                name: String)
    extends Thread(stoppableRunnable,name) {
    def safeStop() = stoppableRunnable.stop()
    def isStopping = stoppableRunnable.isStopping
    def isStopped = stoppableRunnable.isStopped
    def isWorking = !isStopped && !isStopping
  }

  trait StoppableRunnableConsumer[I] extends Runnable { self: LazyLogging =>
    def inQueue: MessageQueue[I]
    private var on = true
    private var stopped = false
    final def stop(): Unit = {
      if (!on) {
        logger warn s"$this already stopped"
      }
      on = false
    }
    final def isStopped = stopped

    final def isStopping = !on && !stopped

    final def run(): Unit = {
      while (on) {
        inQueue.pop().foreach { msg =>
          try onMessage(msg) catch {
            case t: Throwable =>
              logger.error(s"exception while handling inbound message $msg", t)
          }
        }
      }

      stopped = true
      logger info s"stopped"
    }

    def onMessage(m: I) = logger trace s"accepting $m"
    def onStop() = logger info s"stopped"
  }

  import scala.collection.JavaConverters._
  def createInMemoryQueue[T](queueName: String, initial: List[T] = Nil, maxSize:Int = 0) = {
    val innerQueue = if (maxSize > 0) {
      new LinkedBlockingQueue[T](maxSize)
    } else {
      new ConcurrentLinkedQueue[T]()
    }

    innerQueue.addAll(initial.asJava)

    innerQueue match {
      case blockingQueue: LinkedBlockingQueue[T] =>
        new MessageQueue[T] {
          override val name: String = queueName
          override def isEmpty: Boolean = innerQueue.isEmpty
          override def pop(): Option[T] = Option(blockingQueue.take())
          override def push(m: T): Unit = innerQueue.add(m)
          override def push(ms: Seq[T]): Unit = ms.foreach(push)
          override def size: Int = innerQueue.size()
        }
      case _ /*concurrentQueue*/ =>
        new MessageQueue[T] {
          override val name: String = queueName
          override def isEmpty: Boolean = innerQueue.isEmpty
          override def pop(): Option[T] = Option(innerQueue.asInstanceOf[ConcurrentLinkedQueue[T]].poll())
          override def push(m: T): Unit = innerQueue.add(m)
          override def push(ms: Seq[T]): Unit = ms.foreach(push)
          override def size: Int = innerQueue.size()
        }
    }
  }
}
