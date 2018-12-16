package org.kumetix.box.mutable

import com.typesafe.scalalogging.LazyLogging

import scala.collection.mutable

package object stack {

  object Stack {
    def apply[T](innerStack: List[T]): Stack[T] = new Stack[T](innerStack)
    def apply[T](o: T): Stack[T] = new Stack[T](List(o))
  }
  class Stack[T](private var innerStack: List[T]) extends LazyLogging {
    def push(o: T): Unit = {
      innerStack = o :: innerStack
      logger trace s"push [$o] ; ${innerStack.size} elements in stack"
    }
    def pop(): T = {
      val rslt = innerStack.head
      innerStack = innerStack.tail
      logger trace s"pop [$rslt] ; ${innerStack.size} elements in stack"
      rslt
    }
    def nonEmpty: Boolean = innerStack.nonEmpty
    def empty: Boolean = !nonEmpty
  }
}
