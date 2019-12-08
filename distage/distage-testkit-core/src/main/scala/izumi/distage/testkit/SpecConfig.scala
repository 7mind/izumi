package izumi.distage.testkit

import distage.BootstrapModule
import izumi.distage.model.definition
import izumi.distage.model.definition.ModuleBase
import izumi.distage.framework.config.PlanningOptions
import izumi.logstage.api.Log

final case class SpecConfig(
                             contextOptions: PlanningOptions = PlanningOptions(),
                             bootstrapOverrides: BootstrapModule = definition.BootstrapModule.empty,
                             moduleOverrides: ModuleBase = ModuleBase.empty,
                             bootstrapLogLevel: Log.Level = Log.Level.Info,
                           )
