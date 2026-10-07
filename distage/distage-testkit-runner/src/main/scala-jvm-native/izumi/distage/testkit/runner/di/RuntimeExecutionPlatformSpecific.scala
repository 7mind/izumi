package izumi.distage.testkit.runner.di

import scala.collection.mutable.ListBuffer
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.Try

private[di] trait RuntimeExecutionPlatformSpecific[A] { self: RuntimeExecution[A] =>
  final def awaitCompletion(): A = {
    val interruptions = ListBuffer.empty[Throwable]
    var interrupted = false
    var result = Option.empty[Try[A]]
    try {
      while (result.isEmpty) {
        try {
          val _ = Await.ready(completion, Duration.Inf)
          result = completion.value
        } catch {
          case cause: InterruptedException =>
            interrupted = true
            interruptions += cause
            try { val _ = stop() }
            catch { case failure: Throwable => interruptions += failure }
        }
      }
      val settled = result.get
      val failures = settled.failed.toOption.toList ++ interruptions.toList
      failures match {
        case Nil => settled.get
        case cause :: Nil => throw cause
        case primary :: additional =>
          val combined = new RuntimeException("Multiple failures while awaiting test runtime completion", primary)
          additional.foreach(combined.addSuppressed)
          throw combined
      }
    } finally {
      if (interrupted) Thread.currentThread().interrupt()
    }
  }
}
