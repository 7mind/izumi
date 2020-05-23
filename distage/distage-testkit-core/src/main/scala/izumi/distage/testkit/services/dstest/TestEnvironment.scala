package izumi.distage.testkit.services.dstest

import distage._
import izumi.distage.config.model.AppConfig
import izumi.distage.framework.config.PlanningOptions
import izumi.distage.framework.model.ActivationInfo
import izumi.distage.model.definition.Activation
import izumi.distage.model.plan.{OrderedPlan, TriSplittedPlan}
import izumi.distage.roles.model.meta.RolesInfo
import izumi.distage.testkit.services.dstest.DistageTestRunner.DistageTest
import izumi.distage.testkit.services.dstest.TestEnvironment.EnvExecutionParams
import izumi.logstage.api.{IzLogger, Log}

final case class TestEnvironment(
  bsModule: ModuleBase,
  appModule: ModuleBase,
  roles: RolesInfo,
  activationInfo: ActivationInfo,
  activation: Activation,
  memoizationRoots: Set[DIKey],
  forcedRoots: Set[DIKey],
  parallelEnvs: Boolean,
  parallelSuites: Boolean,
  parallelTests: Boolean,
  bootstrapFactory: BootstrapFactory,
  configBaseName: String,
  configOverrides: Option[AppConfig],
  planningOptions: PlanningOptions,
  logLevel: Log.Level,
  debugOutput: Boolean,
) {
  def getExecParams: EnvExecutionParams = {
    EnvExecutionParams(
      parallelEnvs,
      parallelSuites,
      parallelTests,
      planningOptions,
      logLevel,
      debugOutput,
    )
  }
}

object TestEnvironment {

  final case class EnvExecutionParams(
    parallelEnvs: Boolean,
    parallelSuites: Boolean,
    parallelTests: Boolean,
    planningOptions: PlanningOptions,
    logLevel: Log.Level,
    debugOutput: Boolean,
  )

  final case class MemoizationEnvWithPlan(
    envExec: EnvExecutionParams,
    integrationLogger: IzLogger,
    memoizationPlan: TriSplittedPlan,
    runtimePlan: OrderedPlan,
    memoizatonInjector: Injector,
  )

  final case class PreparedTest[F[_]](
    test: DistageTest[F],
    testPlan: OrderedPlan,
    activationInfo: ActivationInfo,
    activation: Activation,
    planner: Planner, // 0.11.0: remove
  )

}
