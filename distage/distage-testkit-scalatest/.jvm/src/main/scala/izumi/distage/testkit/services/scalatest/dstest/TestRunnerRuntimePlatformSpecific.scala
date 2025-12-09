package izumi.distage.testkit.services.scalatest.dstest

import izumi.distage.modules.DefaultModule
import izumi.distage.testkit.model.{DistageTest, EnvResult}
import izumi.distage.testkit.runner.TestkitRunnerModule
import izumi.distage.testkit.runner.api.TestReporter
import izumi.distage.testkit.services.scalatest.dstest.TestRunnerRuntime.{AsyncGlobalSuitesControlHandle, AsyncResult}
import izumi.functional.bio.UnsafeRun2.NamedThreadFactory
import izumi.functional.bio.impl.MiniBIOAsync
import izumi.functional.lifecycle.Lifecycle
import izumi.functional.quasi.{QuasiAsync, QuasiIO, QuasiIORunner}
import izumi.fundamentals.platform.functional.Identity
import izumi.reflect.TagK

import java.util.concurrent.Executors
import scala.concurrent.ExecutionContext

private[dstest] trait TestRunnerRuntimePlatformSpecific {

  final def defaultPlatformRuntimeImpl(): TestRunnerRuntime = {
    defaultBlockingRuntime
  }

  /** Construct blocking test runtime using distage itself. DefaultModule[F] always contains a recipe for `QuasiIORunner[F]` */
  def defaultBlockingRuntimeFor[F[_]: TagK: QuasiIO: QuasiAsync: DefaultModule]: TestRunnerRuntime = {
    blockingRuntimeFor[F](TestRunnerRuntime.defaultRunnerLifecycleFor[F])
  }

  final def defaultBlockingRuntime: TestRunnerRuntime = {
    blockingRuntimeFor[MiniBIOAsync[Throwable, _]](TestRunnerRuntime.miniBIOAsyncTestECLifecycle())
  }

  final def blockingRuntimeFor[F[_]: TagK: QuasiIO: QuasiAsync](
    runnerLifecycle: Lifecycle[Identity, QuasiIORunner[F]]
  ): TestRunnerRuntime = new TestRunnerRuntime {
    override def runTests[F0[_]](
      asyncSuitesHandle: AsyncGlobalSuitesControlHandle,
      testReporter: TestReporter,
      isTestCancellation: Throwable => Boolean,
      testsToRun: Seq[DistageTest[F0]],
    ): Either[List[EnvResult], AsyncResult[List[EnvResult]]] = {
      Left(runnerLifecycle.use {
        _.runBlocking(TestkitRunnerModule.run[F](testReporter, isTestCancellation, testsToRun))
      })
    }
  }

  final def testECLifecycleImpl(): Lifecycle[Identity, ExecutionContext] = {
    val testkitThreadFactory = new NamedThreadFactory("distage-testkit-thread", daemon = true, priority = None)
    Lifecycle
      .fromExecutorService {
        Executors.newCachedThreadPool(testkitThreadFactory)
      }.map(es => ExecutionContext.fromExecutorService(es))
  }

}
