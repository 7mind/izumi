package izumi.distage.testkit.services.dstest

import distage._
import izumi.distage.config.model.AppConfig
import izumi.distage.framework.config.PlanningOptions
import izumi.distage.framework.model.ActivationInfo
import izumi.distage.model.definition.Activation
import izumi.distage.model.plan.TriSplittedPlan
import izumi.distage.roles.model.meta.RolesInfo
import izumi.distage.testkit.services.dstest.TestEnvironment.MemoizationEnvironment
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
  def toMemoizationEnv: MemoizationEnvironment = {
    MemoizationEnvironment(
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

  final case class MemoizationEnvironment(
    parallelEnvs: Boolean,
    parallelSuites: Boolean,
    parallelTests: Boolean,
    planningOptions: PlanningOptions,
    logLevel: Log.Level,
    debugOutput: Boolean,
  )

  final case class MemoizationEnvWithPlan(
    env: MemoizationEnvironment,
    integrationLogger: IzLogger,
    memoizationPlan: TriSplittedPlan,
    runtimePlan: OrderedPlan,
    injector: Injector,
  )

}
