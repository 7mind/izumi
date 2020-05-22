package izumi.distage.testkit.services.dstest

import distage._
import izumi.distage.config.model.AppConfig
import izumi.distage.framework.config.PlanningOptions
import izumi.distage.framework.model.ActivationInfo
import izumi.distage.model.definition.Activation
import izumi.distage.model.plan.{OrderedPlan, TriSplittedPlan}
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
  testRunnerLogLevel: Log.Level,
) {
  def toMemoizationEnv: TestEnvironment.MemoizationEnvironment = {
    TestEnvironment.MemoizationEnvironment(
      bsModule,
      roles,
      activationInfo,
      activation,
      memoizationRoots,
      forcedRoots,
      parallelEnvs,
      parallelSuites,
      parallelTests,
      bootstrapFactory,
      configBaseName,
      configOverrides,
      planningOptions,
      testRunnerLogLevel,
    )
  }
}
object TestEnvironment {
  final case class MemoizationEnvironment(
    bsModule: ModuleBase,
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
    testRunnerLogLevel: Log.Level,
  )

  final case class MemoizationEnvWithPlan(
    env: MemoizationEnvironment,
    testRunnerLogger: IzLogger,
    injector: Injector,
    memoizationPlan: TriSplittedPlan,
    runtimePlan: OrderedPlan,
  )

  final case class AppModuleTestGroup[F[_]](
    appModule: Module,
    distageTests: Seq[DistageTest[F]],
  )

}
