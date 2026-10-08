package candidate

import izumi.distage.testkit.runner.spec.AnyWordSpec
import scala.concurrent.{ExecutionContext, Future}

abstract class TargetSuite extends AnyWordSpec

object Platform {
  def held(body: () => Unit, suite: String): Future[Unit] = {
    val completion = scala.concurrent.Promise[Unit]()
    println("TARGET_HELD_ACQUIRE suite=" + suite)
    scala.scalajs.js.timers.setTimeout(3000) {
      try { body(); completion.success(()) }
      catch { case scala.util.control.NonFatal(cause) => completion.failure(cause) }
      finally println("TARGET_HELD_RELEASE suite=" + suite)
    }
    completion.future
  }
  val context: ExecutionContext = scala.scalajs.concurrent.JSExecutionContext.queue
}
