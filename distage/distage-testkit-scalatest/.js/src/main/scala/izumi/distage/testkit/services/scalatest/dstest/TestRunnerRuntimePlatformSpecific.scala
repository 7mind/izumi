package izumi.distage.testkit.services.scalatest.dstest

import izumi.functional.lifecycle.Lifecycle
import izumi.fundamentals.platform.IzPlatform
import izumi.fundamentals.platform.functional.Identity

import scala.concurrent.ExecutionContext

private[dstest] trait TestRunnerRuntimePlatformSpecific {

  final def defaultPlatformRuntimeImpl(): TestRunnerRuntime = {
    TestRunnerRuntime.defaultAsyncRuntime
  }

  final def testECLifecycleImpl(): Lifecycle[Identity, ExecutionContext] = {
    Lifecycle.pure(IzPlatform.platformGlobalExecutionContext)
  }

}
