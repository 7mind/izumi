package izumi.distage.testkit.runner.di

import distage.{DefaultModule, Injector, ModuleDef, PlannerInput, Roots, TagK}
import izumi.distage.model.definition.ModuleBase
import izumi.distage.testkit.model.{DistageTest, EnvResult}
import izumi.distage.testkit.runner.{Cancellation, TestkitRunnerModule}
import izumi.distage.testkit.runner.api.{TestFinalizationReporter, TestReporter}
import izumi.distage.testkit.runner.impl.{DistageTestRunner, RunnerToF}
import izumi.distage.testkit.runner.impl.services.{TestConfigLoader, TestResourceLifecycle, TestkitLogging}
import izumi.functional.bio.impl.MiniBIOAsync
import izumi.functional.lifecycle.Lifecycle
import izumi.functional.quasi.{QuasiAsync, QuasiIO, QuasiIORunner}
import izumi.fundamentals.platform.IzPlatform
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.language.types.HigherKindedAny.AnyF

import scala.concurrent.{ExecutionContext, Future}

trait TestRunnerRuntime {
  def runTests[F[_]](
    reporter: TestReporter,
    isTestCancellation: Throwable => Boolean,
    tests: Seq[DistageTest[F]],
  ): RuntimeExecution[List[EnvResult]]

  private[di] def prepare(
    reporter: TestReporter,
    finalization: TestFinalizationReporter,
    outerFinalization: TestFinalizationReporter,
    isTestCancellation: Throwable => Boolean,
    tests: Seq[DistageTest[AnyF]],
    configuration: TestConfigLoader,
    options: DistageRunnerOptions,
  ): Future[PreparedTestRuntime] = prepare(reporter, finalization, outerFinalization, isTestCancellation, tests, configuration, options, new Cancellation)

  private[di] def prepare(
    reporter: TestReporter,
    finalization: TestFinalizationReporter,
    outerFinalization: TestFinalizationReporter,
    isTestCancellation: Throwable => Boolean,
    tests: Seq[DistageTest[AnyF]],
    configuration: TestConfigLoader,
    options: DistageRunnerOptions,
    cancellation: Cancellation,
  ): Future[PreparedTestRuntime] = {
    val _ = (reporter, finalization, outerFinalization, isTestCancellation, tests, configuration, options, cancellation)
    Future.failed(new IllegalArgumentException("Test runner runtime does not support owned plan preparation"))
  }
}

object TestRunnerRuntime extends TestRunnerRuntimePlatformSpecific {
  def defaultPlatformRuntime: TestRunnerRuntime = defaultAsyncRuntime

  def defaultAsyncRuntime: TestRunnerRuntime = new AsyncRuntime[MiniBIOAsync[Throwable, _]](runnerLifecycleForMiniBIOAsync(), Nil)

  def defaultAsyncRuntimeFor[F[_]: TagK: QuasiIO: QuasiAsync: DefaultModule]: TestRunnerRuntime = {
    new AsyncRuntime[F](defaultRunnerLifecycleFor[F], Nil)
  }

  def defaultRunnerLifecycleFor[F[_]: TagK: DefaultModule]: Lifecycle[Identity, QuasiIORunner[F]] =
    Injector[Identity]().produceGet[QuasiIORunner[F]](DefaultModule[F])

  def asyncRuntimeFor[F[_]: TagK: QuasiIO: QuasiAsync](
    lifecycle: Lifecycle[Identity, QuasiIORunner[F]],
    runnerOverrides: List[ModuleBase],
  ): TestRunnerRuntime = new AsyncRuntime[F](lifecycle, runnerOverrides)

  private final class AsyncRuntime[F[_]: TagK](
    lifecycle: Lifecycle[Identity, QuasiIORunner[F]],
    runnerOverrides: List[ModuleBase],
  )(implicit F: QuasiIO[F], FA: QuasiAsync[F]) extends TestRunnerRuntime {
    override def runTests[G[_]](
      reporter: TestReporter,
      isTestCancellation: Throwable => Boolean,
      tests: Seq[DistageTest[G]],
    ): RuntimeExecution[List[EnvResult]] = {
      val runtime = new TestRuntime[F](lifecycle, IzPlatform.platformGlobalExecutionContext)
      runtime.run(TestkitRunnerModule.run[F](reporter, isTestCancellation, tests, runnerOverrides))
    }

    override private[di] def prepare(
      reporter: TestReporter,
      finalization: TestFinalizationReporter,
      outerFinalization: TestFinalizationReporter,
      isTestCancellation: Throwable => Boolean,
      tests: Seq[DistageTest[AnyF]],
      configuration: TestConfigLoader,
      options: DistageRunnerOptions,
      cancellation: Cancellation,
    ): Future[PreparedTestRuntime] = {
      val configured = new ModuleDef {
        make[TestConfigLoader].fromValue(configuration)
        make[TestFinalizationReporter].fromValue(finalization)
        make[Boolean].named("izumi.distage.testkit.skip.docker.failures").fromValue(options.skipDockerFailures)
        make[TestkitLogging].fromValue(new TestkitLogging { override def enableDebugOutput: Boolean = options.debugOutput })
        make[RunnerToF[F]].fromValue(new RunnerToF.AsyncImpl[F](F, FA))
      }
      val module = new TestkitRunnerModule[F](reporter, isTestCancellation) overriddenBy configured overriddenBy runnerOverrides.merge
      val producer = Injector.withoutDefaultModule[F]()
      val resource = Lifecycle.liftF(F.maybeSuspendEither(producer.plan(PlannerInput(module, Roots.target[DistageTestRunner[F]])).aggregateErrors))
        .flatMap(plan => new TestResourceLifecycle[F](outerFinalization).produce(producer, plan))
        .evalMap(_.fold(failure => F.fail[DistageTestRunner[F]](failure.toThrowable), locator => F.pure(locator.get[DistageTestRunner[F]])))
      PreparedTestRuntime.acquire(new TestRuntime[F](lifecycle, IzPlatform.platformGlobalExecutionContext), resource, tests, IzPlatform.platformGlobalExecutionContext, cancellation)
    }
  }

  def runnerLifecycleForMiniBIOAsync(): Lifecycle[Identity, QuasiIORunner[MiniBIOAsync[Throwable, _]]] =
    testECLifecycle().map { context =>
      QuasiIORunner.fromBIO[MiniBIOAsync](using MiniBIOAsync.UnsafeRunMiniBIOAsync(using context))
    }

  def testECLifecycle(): Lifecycle[Identity, ExecutionContext] = testECLifecycleImpl()
}
