package izumi.distage.testkit.services.scalatest.dstest

import izumi.functional.bio.UnsafeRun2.NamedThreadFactory
import izumi.functional.lifecycle.Lifecycle
import izumi.fundamentals.platform.functional.Identity

import java.util.concurrent.Executors
import scala.concurrent.ExecutionContext

private[dstest] trait TestRunnerRuntimePlatformSpecific {

  final def defaultPlatformRuntimeImpl(): TestRunnerRuntime = {
    TestRunnerRuntime.defaultAsyncRuntime
  }

  final def testECLifecycleImpl(): Lifecycle[Identity, ExecutionContext] = {
    val testkitThreadFactory = new NamedThreadFactory("distage-testkit-thread", daemon = true, priority = None)
    Lifecycle
      .fromExecutorService {
        Executors.newCachedThreadPool(testkitThreadFactory)
      }.map(es => ExecutionContext.fromExecutorService(es))
  }

}
