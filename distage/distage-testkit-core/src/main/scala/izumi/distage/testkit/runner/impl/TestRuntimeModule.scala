package izumi.distage.testkit.runner.impl

import distage.TagK
import izumi.distage.framework.config.PlanningOptions
import izumi.distage.framework.services.PlanCircularDependencyCheck
import izumi.distage.model.definition.ModuleDef
import izumi.distage.testkit.model.TestEnvironment.EnvExecutionParams
import izumi.distage.testkit.runner.impl.services.{ParTraverseExt, TimedActionF}
import izumi.logstage.api.IzLogger

class TestRuntimeModule[F[_]: TagK](params: EnvExecutionParams) extends ModuleDef {
  make[EnvExecutionParams].fromValue(params)

  // we cannot capture local values using closures, that will break environment merge logic
  make[PlanningOptions].from {
    (exec: EnvExecutionParams) =>
      exec.planningOptions
  }
  make[PlanCircularDependencyCheck]

  make[IzLogger].named("distage-testkit").from {
    (logger: IzLogger) => logger
  }
  // the dependencies will be available through testRunnerLocator which is set as parent for the current injector
  make[TimedActionF[F]].from[TimedActionF.TimedActionFImpl[F]]
  make[TestTreeRunner[F]].from[TestTreeRunner.TestTreeRunnerImpl[F]]
  make[IndividualTestRunner[F]].from[IndividualTestRunner.IndividualTestRunnerImpl[F]]
  make[ParTraverseExt[F]].from[ParTraverseExt.ParTraverseExtImpl[F]]
}
