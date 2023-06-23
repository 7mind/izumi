package izumi.distage.testkit.runner

import distage.{Injector, TagK}
import izumi.distage.model.definition.ModuleDef
import izumi.distage.modules.DefaultModule
import izumi.distage.testkit.model.{DistageTest, EnvResult}
import izumi.distage.testkit.runner.api.TestReporter
import izumi.distage.testkit.runner.impl.services.*
import izumi.distage.testkit.runner.impl.services.TimedAction.TimedActionImpl
import izumi.distage.testkit.runner.impl.services.TimedActionF.TimedActionFImpl
import izumi.distage.testkit.runner.impl.{DistageTestRunner, TestPlanner, TestTreeBuilder}
import izumi.fundamentals.platform.functional.Identity
import izumi.logstage.api.logger.LogQueue
import logstage.ThreadingLogQueue

class TestkitRunnerModule[F[_]: TagK: DefaultModule](reporter: TestReporter, isCancellation: Throwable => Boolean) extends ModuleDef {
  addImplicit[TagK[F]]
  addImplicit[DefaultModule[F]]

  make[TestReporter].fromValue(reporter)
  make[TestkitLogging]

  make[Throwable => Boolean].fromValue(isCancellation)
  make[TestStatusConverter]
  make[TimedAction].from[TimedActionImpl]
  make[TimedActionF[Identity]].from[TimedActionFImpl[Identity]]
  make[TestConfigLoader].from[TestConfigLoader.TestConfigLoaderImpl]
  make[TestPlanner[F]]
  make[TestTreeBuilder[F]].from[TestTreeBuilder.TestTreeBuilderImpl[F]]
  make[ExtParTraverse[Identity]].from[ExtParTraverse.ExtParTraverseImpl[Identity]]

  make[DistageTestRunner[F]].from[DistageTestRunner[F]]
  make[LogQueue].fromResource(ThreadingLogQueue.resource())
}

object TestkitRunnerModule {
  def run[F[_]: TagK: DefaultModule](reporter: TestReporter, isCancellation: Throwable => Boolean, tests: Seq[DistageTest[F]]): Identity[List[EnvResult]] = {
    Injector()
      .produceRun(new TestkitRunnerModule[F](reporter, isCancellation)) {
        (runner: DistageTestRunner[F]) =>
          runner.run(tests)
      }
  }
}
