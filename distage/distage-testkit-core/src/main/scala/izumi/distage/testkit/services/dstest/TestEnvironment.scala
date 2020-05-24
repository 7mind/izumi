package izumi.distage.testkit.services.dstest

import distage._
import izumi.distage.config.model.AppConfig
import izumi.distage.framework.config.PlanningOptions
import izumi.distage.framework.model.ActivationInfo
import izumi.distage.model.definition.Activation
import izumi.distage.model.plan.{OrderedPlan, TriSplittedPlan}
import izumi.distage.roles.model.meta.RolesInfo
import izumi.distage.testkit.services.dstest.DistageTestRunner.DistageTest
import izumi.distage.testkit.services.dstest.TestEnvironment.{EnvExecutionParams, ParallelLevel}
import izumi.logstage.api.{IzLogger, Log}

final case class TestEnvironment(
  bsModule: ModuleBase,
  appModule: ModuleBase,
  roles: RolesInfo,
  activationInfo: ActivationInfo,
  activation: Activation,
  memoizationRoots: Set[DIKey],
  forcedRoots: Set[DIKey],
  parallelEnvs: ParallelLevel,
  bootstrapFactory: BootstrapFactory,
  configBaseName: String,
  configOverrides: Option[AppConfig],
  planningOptions: PlanningOptions,
  logLevel: Log.Level,
)(// exclude from `equals` test-runner only parameters that do not affect the memoization plan and
  // that are not used in [[DistageTestRunner.groupEnvs]] grouping to allow merging more envs
  val parallelSuites: ParallelLevel,
  val parallelTests: ParallelLevel,
  val debugOutput: Boolean,
) {
  def getExecParams: EnvExecutionParams = {
    EnvExecutionParams(
      parallelEnvs,
      planningOptions,
      logLevel,
    )
  }
}

object TestEnvironment {
  sealed trait ParallelLevel
  object ParallelLevel {
    final case class Fixed(n: Int) extends ParallelLevel
    case object Unlimited extends ParallelLevel
    case object Sequential extends ParallelLevel
  }

  final case class EnvExecutionParams(
    parallelEnvs: ParallelLevel,
    planningOptions: PlanningOptions,
    logLevel: Log.Level,
  )

  final case class MemoizationEnvWithPlan(
    envExec: EnvExecutionParams,
    integrationLogger: IzLogger,
    memoizationPlan: TriSplittedPlan,
    runtimePlan: OrderedPlan,
    memoizatonInjector: Injector,
    highestDebugOutputInTests: Boolean,
  )

  final case class PreparedTest[F[_]](
    test: DistageTest[F],
    testPlan: OrderedPlan,
    activationInfo: ActivationInfo,
    activation: Activation,
    planner: Planner, // 0.11.0: remove
  )

}
