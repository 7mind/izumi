package izumi.functional.bio.test

import izumi.functional.bio.impl.MiniBIOAsync
import izumi.functional.bio.{Exit, F}
import org.scalatest.wordspec.AsyncWordSpec

import scala.concurrent.{ExecutionContext, Promise}
import scala.concurrent.duration.*
import scala.util.Success

class MiniBIOAsyncSleepTest extends AsyncWordSpec {

  import MiniBIOAsync.WeakAsyncForMiniBIOAsync

  override implicit def executionContext: ExecutionContext = ExecutionContext.global

  "MiniBIOAsync" should {

    "sleep should complete and allow continuation" in {
      val effect = F.sleep(10.millis)

      effect.runOnEC(executionContext).map {
        case Exit.Success(_) => succeed
        case e => fail(s"Expected Success but got: $e")
      }
    }

    "sleep should not block the execution context" in {
      val promise = Promise[Unit]()

      val effect = {
        F.zipPar(
          F.flatMap(F.sleep(1.second))(_ => F.fromFuture(promise.future)),
          F.sync(promise.complete(Success(()))),
        )
      }

      effect.runOnEC(executionContext).map {
        case Exit.Success(_) => succeed
        case e => fail(s"Expected Success but got: $e")
      }
    }

  }

}
