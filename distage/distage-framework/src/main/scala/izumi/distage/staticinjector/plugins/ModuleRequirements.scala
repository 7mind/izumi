package izumi.distage.staticinjector.plugins

import distage.DIKey
import izumi.logstage.api.logger.LogRouter
import logstage.di.LogstageModule

class ModuleRequirements(val requiredKeys: Set[DIKey])

final class NoModuleRequirements extends ModuleRequirements(Set.empty)

final class LogstageModuleRequirements
  extends ModuleRequirements(
    new LogstageModule(LogRouter.nullRouter, false).keys
  )
