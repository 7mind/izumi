package izumi.distage.testkit.services.scalatest.dstest

import izumi.functional.bio.Bifunctorized
import izumi.functional.lifecycle.Lifecycle
import izumi.fundamentals.platform.IzPlatform

import scala.concurrent.ExecutionContext

private[dstest] trait TestRunnerRuntimePlatformSpecific {

  final def defaultPlatformRuntimeImpl(): TestRunnerRuntime = {
    TestRunnerRuntime.defaultAsyncRuntime
  }

  final def testECLifecycleImpl(): Lifecycle[Bifunctorized.IdentityBifunctorized, Throwable, ExecutionContext] = {
    Lifecycle.pure[Bifunctorized.IdentityBifunctorized, Throwable, ExecutionContext](IzPlatform.platformGlobalExecutionContext)
  }

}
