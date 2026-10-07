package izumi.distage.testkit.runner.di

import izumi.functional.bio.UnsafeRun2.NamedThreadFactory
import izumi.functional.lifecycle.Lifecycle
import izumi.fundamentals.platform.functional.Identity

import java.util.concurrent.Executors
import scala.concurrent.ExecutionContext

private[di] trait TestRunnerRuntimePlatformSpecific {

  final def testECLifecycleImpl(): Lifecycle[Identity, ExecutionContext] = {
    val testkitThreadFactory = new NamedThreadFactory("distage-testkit-thread", daemon = true, priority = None)
    Lifecycle
      .fromExecutorService {
        Executors.newCachedThreadPool(testkitThreadFactory)
      }.map(es => ExecutionContext.fromExecutorService(es))
  }

}
