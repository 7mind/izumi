package izumi.distage.testkit.runner

import scala.collection.mutable.ListBuffer
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration
import scala.util.Try

private[runner] object CompletionAwaiter {
  def await[A](completion: Future[A], cancel: () => Unit, message: String): A = {
    val interruptions = ListBuffer.empty[Throwable]
    var result = Option.empty[Try[A]]
    try {
      while (result.isEmpty) {
        try {
          val _ = Await.ready(completion, Duration.Inf)
          result = completion.value
        } catch {
          case cause: InterruptedException =>
            interruptions += cause
            try cancel() catch { case failure: Throwable => interruptions += failure }
        }
      }
      val settled = result.get
      (settled.failed.toOption.toList ++ interruptions.toList) match {
        case Nil => settled.get
        case cause :: Nil => throw cause
        case primary :: additional =>
          val combined = new RuntimeException(message, primary)
          additional.foreach(combined.addSuppressed)
          throw combined
      }
    } finally {
      if (interruptions.nonEmpty) Thread.currentThread().interrupt()
    }
  }
}
