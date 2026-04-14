package izumi.distage.testkit.runner

import distage.{Injector, TagK}
import izumi.distage.model.definition.{ModuleBase, ModuleDef}
import izumi.distage.testkit.DebugProperties
import izumi.distage.testkit.model.{DistageTest, EnvResult}
import izumi.distage.testkit.runner.api.TestReporter
import izumi.distage.testkit.runner.impl.services.*
import izumi.distage.testkit.runner.impl.services.TimedActionF.TimedActionFImpl
import izumi.distage.testkit.runner.impl.{DistageTestRunner, RunnerToF, TestPlanner, TestTreeBuilder}
import izumi.functional.quasi.{QuasiAsync, QuasiIO}
import izumi.fundamentals.platform.IzPlatform
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.language.types.HigherKindedAny.AnyF
import izumi.logstage.api.logger.LogQueue
import logstage.ThreadingLogQueue

class TestkitRunnerModule[F[_]: TagK: QuasiIO: QuasiAsync](
  reporter: TestReporter,
  isTestCancellation: Throwable => Boolean,
) extends ModuleDef {
  addImplicit[TagK[F]]
  addImplicit[QuasiIO[F]]
  addImplicit[QuasiAsync[F]]
  make[TestReporter].fromValue(reporter)

  make[Throwable => Boolean].fromValue(isTestCancellation)
  make[Boolean].named("izumi.distage.testkit.skip.docker.failures").from {
    DebugProperties.`izumi.distage.testkit.skip.docker.failures`.boolValue(default = false) ||
    IzPlatform.getenvOption("IZUMI_SKIP_DOCKER_FAILURES").contains("true")
  }
  make[TestStatusConverter]

  make[TestkitLogging]

  make[TimedActionF[Identity]].from[TimedActionFImpl[Identity]]
  make[TestConfigLoader].from[TestConfigLoader.TestConfigLoaderImpl]

  make[TestPlanner]
  make[TestTreeBuilder].from[TestTreeBuilder.TestTreeBuilderImpl]

  make[TimedActionF[F]].from[TimedActionFImpl[F]]
  make[ParTraverseExt[F]].from[ParTraverseExt.ParTraverseExtImpl[F]]

  make[RunnerToF[F]].from[RunnerToF.PlatformDefaultImpl[F]]
  make[DistageTestRunner[F]].from[DistageTestRunner[F]]

  make[LogQueue].fromResource(ThreadingLogQueue.resource())
}

object TestkitRunnerModule {
  /**
    * Run tests in Any effect into F effect, where F is usually `Identity`
    *
    * If `F` is incapable of async (e.g. `Identity`), tests will run via F's equivalent of unsafePerformIO and will
    * block the running thread. Test parallelism in Identity is achieved via thread pools, which is probably OK for tests.
    *
    * @param isTestCancellation Predicate for determining whether a thrown exception signifies a canceled, not failed, test.
    *                           e.g. For ScalaTest it's `_.isInstanceOf[org.scalatest.exceptions.TestCanceledException]`
    *
    * @note a `DistageTest[G]` will be run using `QuasiIORunner[G]` assembled from bindings in [[DistageTest.environment]]
    *       (Most likely the QuasIORunner binding will be found in [[izumi.distage.testkit.model.TestEnvironment.defaultModule]],
    *       as DefaultModule instances must provide a `QuasiIORunner`)
    */
  def run[F[_]: TagK: QuasiIO: QuasiAsync](
    reporter: TestReporter,
    isTestCancellation: Throwable => Boolean,
    tests: Seq[DistageTest[AnyF]],
    runnerOverrides: List[ModuleBase],
  ): F[List[EnvResult]] = {
    val runnerModule = new TestkitRunnerModule[F](reporter, isTestCancellation) overriddenBy runnerOverrides.merge
    Injector
      .withoutDefaultModule[F]()
      .produceRun(runnerModule) {
        (runner: DistageTestRunner[F]) =>
          runner.run(tests)
      }
  }
}
