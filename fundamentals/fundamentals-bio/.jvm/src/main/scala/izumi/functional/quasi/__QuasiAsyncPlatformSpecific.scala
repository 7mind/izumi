package izumi.functional.quasi

import izumi.functional.bio.UnsafeRun2.NamedThreadFactory
import izumi.functional.lifecycle.Lifecycle
import izumi.fundamentals.platform.functional.Identity

import java.util.concurrent.Executors
import scala.concurrent.ExecutionContext

private[izumi] object __QuasiAsyncPlatformSpecific {
  private val factory = new NamedThreadFactory("QuasiIO-cached-pool", daemon = true)

  private[izumi] final lazy val QuasiAsyncIdentityBlockingIOPool = ExecutionContext.fromExecutorService {
    Executors.newCachedThreadPool(factory)
  }

  private[izumi] final def QuasiAsyncIdentityCreateLimitedThreadPool(max: Int): Lifecycle[Identity, ExecutionContext] = {
    Lifecycle
      .fromExecutorService {
        Executors.newFixedThreadPool(max, factory)
      }.map(ExecutionContext.fromExecutorService)
  }
}
