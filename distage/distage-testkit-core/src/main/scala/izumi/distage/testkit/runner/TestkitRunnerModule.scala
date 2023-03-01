package izumi.distage.testkit.runner

import distage.{Injector, TagK}
import izumi.distage.model.definition.ModuleDef
import izumi.distage.modules.DefaultModule
import izumi.distage.testkit.model.{DistageTest, EnvResult}
import izumi.distage.testkit.runner.api.TestReporter
import izumi.distage.testkit.runner.impl.services.TimedAction.TimedActionImpl
import izumi.distage.testkit.runner.impl.services.{TestConfigLoader, TestStatusConverter, TestkitLogging, TimedAction}
import izumi.distage.testkit.runner.impl.{DistageTestRunner, IndividualTestRunner, TestPlanner}
import izumi.functional.quasi.QuasiIO
import izumi.fundamentals.platform.functional.Identity

class TestkitRunnerModule[F[_]: QuasiIO: TagK: DefaultModule](reporter: TestReporter, isCancellation: Throwable => Boolean) extends ModuleDef {
  addImplicit[TagK[F]]
  addImplicit[DefaultModule[F]]
  addImplicit[QuasiIO[F]]
  make[TestReporter].fromValue(reporter)
  make[TestkitLogging]
  make[Throwable => Boolean].fromValue(isCancellation)
  make[TestStatusConverter[F]]
  make[TimedAction[F]].from[TimedActionImpl[F]]
  make[TimedAction[Identity]].from[TimedActionImpl[Identity]]
  make[IndividualTestRunner[F]].from[IndividualTestRunner.IndividualTestRunnerImpl[F]]
  make[DistageTestRunner[F]].from[DistageTestRunner[F]]
  make[TestConfigLoader].from[TestConfigLoader.TestConfigLoaderImpl]
  make[TestPlanner[F]]
}

object TestkitRunnerModule {
  def run[F[_]: QuasiIO: TagK: DefaultModule](reporter: TestReporter, isCancellation: Throwable => Boolean, tests: Seq[DistageTest[F]]): Identity[List[EnvResult]] = {
    Injector().produceRun(new TestkitRunnerModule[F](reporter, isCancellation)) {
      (runner: DistageTestRunner[F]) =>
        runner.run(tests)
    }
  }
}
