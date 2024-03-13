package izumi.functional.quasi

import izumi.functional.lifecycle.Lifecycle
import izumi.fundamentals.platform.functional.Identity

import scala.annotation.unused
import scala.concurrent.ExecutionContext

private[izumi] object __QuasiAsyncPlatformSpecific {
  private[izumi] final def QuasiAsyncIdentityBlockingIOPool: ExecutionContext = ExecutionContext.Implicits.global
  private[izumi] final def QuasiAsyncIdentityCreateLimitedThreadPool(@unused max: Int): Lifecycle[Identity, ExecutionContext] = {
    Lifecycle.pure(QuasiAsyncIdentityBlockingIOPool)
  }
}
