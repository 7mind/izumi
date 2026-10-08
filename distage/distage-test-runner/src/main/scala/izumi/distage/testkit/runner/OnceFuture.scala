package izumi.distage.testkit.runner

import scala.concurrent.{Future, Promise}

private[runner] final class OnceFuture[A](monitor: Object) {
  private var completion = Option.empty[Promise[A]]

  def isStarted: Boolean = monitor.synchronized(completion.nonEmpty)

  // Publish admission and capture owner state under its monitor; callbacks run outside it.
  // Synchronous exceptions propagate after admission; only returned Futures complete the promise.
  def apply[S](snapshot: => S)(action: S => Future[A]): Future[A] = {
    val (result, admitted) = monitor.synchronized {
      completion match {
        case Some(previous) => (previous.future, None)
        case None =>
          val requested = Promise[A]()
          completion = Some(requested)
          (requested.future, Some((requested, snapshot)))
      }
    }
    admitted.foreach { case (requested, state) =>
      val _ = requested.completeWith(action(state))
    }
    result
  }
}
