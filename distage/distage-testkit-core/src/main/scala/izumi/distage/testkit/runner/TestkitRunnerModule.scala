package izumi.distage.testkit.runner

import distage.{Injector, TagK}
import izumi.distage.model.Planner
import izumi.distage.model.definition.ModuleDef
import izumi.distage.modules.DefaultModule
import izumi.distage.testkit.model.{DistageTest, EnvResult}
import izumi.distage.testkit.runner.api.TestReporter
import izumi.distage.testkit.runner.impl.services.TimedAction.TimedActionImpl
import izumi.distage.testkit.runner.impl.services.{TestConfigLoader, TestStatusConverter, TestkitLogging, TimedAction}
import izumi.distage.testkit.runner.impl.{DistageTestRunner, IndividualTestRunner, TestPlanner, TestTreeBuilder}
import izumi.fundamentals.platform.functional.Identity

class TestkitRunnerModule[F[_]: TagK: DefaultModule](reporter: TestReporter, isCancellation: Throwable => Boolean) extends ModuleDef {
  addImplicit[TagK[F]]
  addImplicit[DefaultModule[F]]
  make[TestReporter].fromValue(reporter)
  make[TestkitLogging]
  make[Throwable => Boolean].fromValue(isCancellation)
  make[TestStatusConverter]
  make[TimedAction].from[TimedActionImpl]
  make[IndividualTestRunner].from[IndividualTestRunner.IndividualTestRunnerImpl]
  make[DistageTestRunner[F]].from[DistageTestRunner[F]]
  make[TestConfigLoader].from[TestConfigLoader.TestConfigLoaderImpl]
  make[TestPlanner[F]]
  make[TestTreeBuilder[F]].from[TestTreeBuilder.TestTreeBuilderImpl[F]]
  make[Planner].fromValue(Injector())
}

object TestkitRunnerModule {
  def run[F[_]: TagK: DefaultModule](reporter: TestReporter, isCancellation: Throwable => Boolean, tests: Seq[DistageTest[F]]): Identity[List[EnvResult]] = {
    Injector().produceRun(new TestkitRunnerModule[F](reporter, isCancellation)) {
      (runner: DistageTestRunner[F]) =>
        runner.run(tests)
    }
  }
}
