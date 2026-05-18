package izumi.distage.testkit.services.scalatest.dstest

import izumi.functional.bio.{Bifunctorized, UnsafeRun2}
import izumi.functional.lifecycle.Lifecycle

import java.util.concurrent.Executors
import scala.concurrent.ExecutionContext

private[dstest] trait TestRunnerRuntimePlatformSpecific {

  final def defaultPlatformRuntimeImpl(): TestRunnerRuntime = {
    TestRunnerRuntime.defaultAsyncRuntime
  }

  final def testECLifecycleImpl(): Lifecycle[Bifunctorized.IdentityBifunctorized, Throwable, ExecutionContext] = {
    val testkitThreadFactory = new UnsafeRun2.NamedThreadFactory("distage-testkit-thread", daemon = true, priority = None)
    Lifecycle
      .fromExecutorService {
        Executors.newCachedThreadPool(testkitThreadFactory)
      }.map(es => ExecutionContext.fromExecutorService(es))
  }

}
