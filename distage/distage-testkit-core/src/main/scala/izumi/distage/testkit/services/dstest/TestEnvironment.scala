package izumi.distage.testkit.services.dstest

import distage._
import izumi.distage.config.model.AppConfig
import izumi.distage.framework.config.PlanningOptions
import izumi.distage.framework.model.ActivationInfo
import izumi.distage.model.definition.Activation
import izumi.distage.model.plan.TriSplittedPlan
import izumi.distage.roles.model.meta.RolesInfo
import izumi.distage.testkit.services.dstest.DistageTestRunner.DistageTest
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
  verboseTestRunner: Boolean = false,
) {
  def toMemoizationEnv: TestEnvironment.MemoizationEnvironment = {
    TestEnvironment.MemoizationEnvironment(
      parallelEnvs,
      parallelSuites,
      parallelTests,
      planningOptions,
      logLevel,
      verboseTestRunner,
    )
  }
}

object TestEnvironment {
  trait TestRunnerLog
  object TestRunnerLog {
    case object Normal extends TestRunnerLog
    case object Debug extends TestRunnerLog
    case object Verbose extends TestRunnerLog
  }

  final case class MemoizationEnvironment(
    parallelEnvs: Boolean,
    parallelSuites: Boolean,
    parallelTests: Boolean,
    planningOptions: PlanningOptions,
    testRunnerLogLevel: Log.Level,
    verboseTestRunner: Boolean,
  )

  final case class MemoizationEnvWithPlan(
    env: MemoizationEnvironment,
    runtimeLogger: IzLogger,
    testEnvLogger: IzLogger,
    memoizationPlan: TriSplittedPlan,
    runtimePlan: OrderedPlan,
    injector: Injector,
  )

  final case class AppModuleTestGroup[F[_]](
    appModule: Module,
    testPlan: OrderedPlan,
    distageTests: DistageTest[F],
  )

}
