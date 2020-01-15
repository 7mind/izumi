package izumi.distage.testkit

import distage.{BootstrapModule, ModuleBase}
import izumi.distage.config.model.AppConfig
import izumi.distage.framework.config.PlanningOptions
import izumi.logstage.api.Log

final case class SpecConfig(
                             planningOptions: PlanningOptions = PlanningOptions(),
                             bootstrapLogLevel: Log.Level = Log.Level.Info,
                             moduleOverrides: ModuleBase = ModuleBase.empty,
                             bootstrapOverrides: BootstrapModule = BootstrapModule.empty,
                             configOverrides: AppConfig => AppConfig = identity,
                           )
