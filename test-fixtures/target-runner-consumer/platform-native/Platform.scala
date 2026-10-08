package candidate

import izumi.distage.testkit.runner.spec.AnyWordSpec
import scala.concurrent.{ExecutionContext, Future}

abstract class TargetSuite extends AnyWordSpec

object Platform {
  def held(body: () => Unit, suite: String): Future[Unit] = Future {
    println("TARGET_HELD_ACQUIRE suite=" + suite)
    try { Thread.sleep(3000L); body() }
    finally println("TARGET_HELD_RELEASE suite=" + suite)
  }(context)
  val context: ExecutionContext = ExecutionContext.global
}
