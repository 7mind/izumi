package izumi.distage.testkit.services.dstest

import distage._
import izumi.distage.config.model.AppConfig
import izumi.distage.framework.config.PlanningOptions
import izumi.distage.framework.model.ActivationInfo
import izumi.distage.model.definition.Activation
import izumi.distage.roles.model.meta.RolesInfo
import izumi.logstage.api.Log

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
  def toMemoizationGroup: TestEnvironment.EnvironmentGroup = {
    TestEnvironment.EnvironmentGroup(
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
  final case class EnvironmentGroup(
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

}
