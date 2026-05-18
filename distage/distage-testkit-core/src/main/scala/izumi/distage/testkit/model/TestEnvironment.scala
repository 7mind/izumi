package izumi.distage.testkit.model

import izumi.distage.config.model.AppConfig
import izumi.distage.framework.config.PlanningOptions
import izumi.distage.framework.model.ActivationInfo
import izumi.distage.model.definition.{Activation, Module, ModuleBase}
import izumi.distage.modules.DefaultModule
import izumi.distage.roles.model.meta.RolesInfo
import izumi.distage.testkit.model.TestConfig.{AxisDIKeys, Parallelism, PriorityAxisDIKeys}
import izumi.distage.testkit.model.TestEnvironment.EnvExecutionParams
import izumi.distage.testkit.runner.impl.services.BootstrapFactory
import izumi.fundamentals.platform.language.types.HigherKindedAny.AnyF2
import izumi.logstage.api.Log
import izumi.reflect.TagKK

/**
  * [[TestConfig]] allows the user to define test settings.
  *
  * These settings are turned into [[TestEnvironment]] before they are handled by the test runner.
  *
  * This process happens automatically and the user is not expected to directly interact with [[TestEnvironment]]
  */
final case class TestEnvironment(
  bsModule: ModuleBase,
  appModule: ModuleBase,
  effectType: TagKK[AnyF2],
  defaultModule: Module,
  roles: RolesInfo,
  activationInfo: ActivationInfo,
  activation: Activation,
  memoizationRoots: PriorityAxisDIKeys,
  forcedRoots: AxisDIKeys,
  parallelEnvs: Parallelism,
  bootstrapFactory: BootstrapFactory,
  configBaseName: String,
  configOverrides: Option[AppConfig],
  planningOptions: PlanningOptions,
  logLevel: Log.Level,
  activationStrategy: TestActivationStrategy,
)(// exclude from `equals` test-runner-only parameters that do not affect the memoization plan and
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
      effectType,
      defaultModule,
    )
  }
}

object TestEnvironment {
  sealed abstract class EnvExecutionParams {
    type F[+_, +_]
    val parallelEnvs: Parallelism
    val planningOptions: PlanningOptions
    val logLevel: Log.Level
    implicit val effectType: TagKK[F]
    implicit val defaultModule: DefaultModule[F]
  }
  object EnvExecutionParams {
    type Aux[F0[+_, +_]] = EnvExecutionParams { type F[+E, +A] = F0[E, A] }
    // @formatter:off
    def apply[F0[+_, +_]](parallelEnvs: Parallelism, planningOptions: PlanningOptions, logLevel: Log.Level, effectType: TagKK[F0], defaultModule: Module): EnvExecutionParams.Aux[F0] = {
      final case class EnvExecutionParamsImpl(parallelEnvs: Parallelism, planningOptions: PlanningOptions, logLevel: Log.Level, effectType: TagKK[F0], defaultModule: DefaultModule[F0]) extends EnvExecutionParams {
        override type F[+E, +A] = F0[E, A]
      }
      EnvExecutionParamsImpl(parallelEnvs, planningOptions, logLevel, effectType, DefaultModule[F0](defaultModule))
    }
    // @formatter:on
  }
}
