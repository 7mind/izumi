package izumi.distage.testkit.runner.di

import distage.{DefaultModule, Injector, TagK}
import izumi.distage.model.definition.ModuleBase
import izumi.distage.testkit.model.{DistageTest, EnvResult}
import izumi.distage.testkit.runner.TestkitRunnerModule
import izumi.distage.testkit.runner.api.TestReporter
import izumi.functional.bio.impl.MiniBIOAsync
import izumi.functional.lifecycle.Lifecycle
import izumi.functional.quasi.{QuasiAsync, QuasiIO, QuasiIORunner}
import izumi.fundamentals.platform.IzPlatform
import izumi.fundamentals.platform.functional.Identity

import scala.concurrent.ExecutionContext

trait TestRunnerRuntime {
  def runTests[F[_]](
    reporter: TestReporter,
    isTestCancellation: Throwable => Boolean,
    tests: Seq[DistageTest[F]],
  ): RuntimeExecution[List[EnvResult]]
}

object TestRunnerRuntime extends TestRunnerRuntimePlatformSpecific {
  def defaultAsyncRuntime: TestRunnerRuntime = asyncRuntimeFor[MiniBIOAsync[Throwable, _]](runnerLifecycleForMiniBIOAsync(), Nil)

  def defaultAsyncRuntimeFor[F[_]: TagK: QuasiIO: QuasiAsync: DefaultModule]: TestRunnerRuntime = asyncRuntimeFor[F](defaultRunnerLifecycleFor[F], Nil)

  def defaultRunnerLifecycleFor[F[_]: TagK: DefaultModule]: Lifecycle[Identity, QuasiIORunner[F]] =
    Injector[Identity]().produceGet[QuasiIORunner[F]](DefaultModule[F])

  def asyncRuntimeFor[F[_]: TagK: QuasiIO: QuasiAsync](
    lifecycle: Lifecycle[Identity, QuasiIORunner[F]],
    runnerOverrides: List[ModuleBase],
  ): TestRunnerRuntime = new TestRunnerRuntime {
    override def runTests[G[_]](
      reporter: TestReporter,
      isTestCancellation: Throwable => Boolean,
      tests: Seq[DistageTest[G]],
    ): RuntimeExecution[List[EnvResult]] = {
      val runtime = new TestRuntime[F](lifecycle, IzPlatform.platformGlobalExecutionContext)
      runtime.run(TestkitRunnerModule.run[F](reporter, isTestCancellation, tests, runnerOverrides))
    }
  }

  def runnerLifecycleForMiniBIOAsync(): Lifecycle[Identity, QuasiIORunner[MiniBIOAsync[Throwable, _]]] =
    testECLifecycle().map { context =>
      QuasiIORunner.fromBIO[MiniBIOAsync](using MiniBIOAsync.UnsafeRunMiniBIOAsync(using context))
    }

  def testECLifecycle(): Lifecycle[Identity, ExecutionContext] = testECLifecycleImpl()
}
