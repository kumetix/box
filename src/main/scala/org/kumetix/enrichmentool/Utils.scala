package org.kumetix.enrichmentool

import java.io.Closeable

object Utils {
  /**
    * @param is
    * @param f
    * @tparam R closeable stream
    * @tparam T single emitted item
    */
  def using[R <: Closeable, T](is: R)(f: R => T): Unit = {
    try {
      f(is)
    } finally {
      is.close()
    }
  }
}
