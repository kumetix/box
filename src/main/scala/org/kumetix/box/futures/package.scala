package org.kumetix.box

import scala.concurrent.{ExecutionContext, Future}

package object futures {
  def asyncComputationStat(futures: List[Future[_]])(implicit ec: ExecutionContext) = {
    futures.foldLeft(Future(0, 0)) { case (acc, f) =>
      acc flatMap { case (completed, inProgress) =>
        val newCompleted = completed + (if (f.isCompleted) 1 else 0)
        val newInProgress = inProgress + (if (!f.isCompleted) 1 else 0)
        Future.successful((newCompleted, newInProgress))
      }
    }
  }
}
