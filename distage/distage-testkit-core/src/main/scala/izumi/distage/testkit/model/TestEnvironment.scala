package izumi.distage.testkit.model

import distage.*
import izumi.distage.config.model.AppConfig
import izumi.distage.framework.config.PlanningOptions
import izumi.distage.framework.model.ActivationInfo
import izumi.distage.model.definition.Activation
import izumi.distage.roles.model.meta.RolesInfo
import izumi.distage.testkit.model.TestConfig.{AxisDIKeys, Parallelism, PriorAxisDIKeys}
import izumi.distage.testkit.model.TestEnvironment.EnvExecutionParams
import izumi.distage.testkit.runner.impl.services.BootstrapFactory
import izumi.logstage.api.Log

/**
  * [[TestConfig]] allows the user to define test settings.
  *
  * These settings should be turned into [[TestEnvironment]] before they can be handled by the test runner.
  *
  * This process happens automatically and the user is not expected to directly interact with [[TestEnvironment]]
  */
final case class TestEnvironment(
                                  bsModule: ModuleBase,
                                  appModule: ModuleBase,
                                  roles: RolesInfo,
                                  activationInfo: ActivationInfo,
                                  activation: Activation,
                                  memoizationRoots: PriorAxisDIKeys,
                                  forcedRoots: AxisDIKeys,
                                  parallelEnvs: Parallelism,
                                  bootstrapFactory: BootstrapFactory,
                                  configBaseName: String,
                                  configOverrides: Option[AppConfig],
                                  planningOptions: PlanningOptions,
                                  logLevel: Log.Level,
                                  activationStrategy: TestActivationStrategy,
)( // exclude from `equals` test-runner only parameters that do not affect the memoization plan and
   // that are not used in [[DistageTestRunner.groupEnvs]] grouping to allow merging more envs
   val parallelSuites: Parallelism,
   val parallelTests: Parallelism,
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
  final case class EnvExecutionParams(
                                       parallelEnvs: Parallelism,
                                       planningOptions: PlanningOptions,
                                       logLevel: Log.Level,
  )
}
