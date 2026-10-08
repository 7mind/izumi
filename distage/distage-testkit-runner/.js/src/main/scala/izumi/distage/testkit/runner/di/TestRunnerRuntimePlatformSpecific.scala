package izumi.distage.testkit.runner.di

import izumi.functional.lifecycle.Lifecycle
import izumi.fundamentals.platform.IzPlatform
import izumi.fundamentals.platform.functional.Identity

import scala.concurrent.ExecutionContext

private[di] trait TestRunnerRuntimePlatformSpecific {

  final def testECLifecycleImpl(): Lifecycle[Identity, ExecutionContext] = {
    Lifecycle.pure(IzPlatform.platformGlobalExecutionContext)
  }

}
