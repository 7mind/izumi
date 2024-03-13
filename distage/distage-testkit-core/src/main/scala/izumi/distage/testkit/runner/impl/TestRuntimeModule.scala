package izumi.distage.testkit.runner.impl

import distage.TagK
import izumi.distage.framework.config.PlanningOptions
import izumi.distage.framework.services.PlanCircularDependencyCheck
import izumi.distage.model.definition.ModuleDef
import izumi.distage.testkit.model.TestEnvironment.EnvExecutionParams
import izumi.distage.testkit.runner.impl.services.TimedActionF.TimedActionFImpl
import izumi.distage.testkit.runner.impl.services.{ExtParTraverse, TimedActionF}
import izumi.logstage.api.IzLogger

class TestRuntimeModule[F[_]: TagK](params: EnvExecutionParams) extends ModuleDef {
  make[EnvExecutionParams].fromValue(params)
  make[PlanningOptions].from {
    (exec: EnvExecutionParams) =>
      exec.planningOptions
  }

  // we cannot capture local values here, that will break environment merge logic
  make[PlanCircularDependencyCheck]
  make[IzLogger].named("distage-testkit").from {
    (logger: IzLogger) => logger
  }
  // the dependencies will be available through testRunnerLocator which is set as parent for the current injector
  make[TimedActionF[F]].from[TimedActionFImpl[F]]
  make[TestTreeRunner[F]].from[TestTreeRunner.TestTreeRunnerImpl[F]]
  make[IndividualTestRunner[F]].from[IndividualTestRunner.IndividualTestRunnerImpl[F]]
  make[ExtParTraverse[F]].from[ExtParTraverse.ExtParTraverseImpl[F]]
}
