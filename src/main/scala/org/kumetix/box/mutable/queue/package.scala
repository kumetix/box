package org.kumetix.box.mutable

import java.util.concurrent.{ConcurrentLinkedDeque, ConcurrentLinkedQueue}

package object queue {
  object LimitedConcurrentQueue {
    def apply[T](maxSize: Int) = new LimitedConcurrentQueue[T](maxSize)
  }
  class LimitedConcurrentQueue[T](private val maxSize: Int) {
    private val queue = new ConcurrentLinkedQueue[T]
    def enqueue(bulk: List[T]) = {

    }
  }
}
