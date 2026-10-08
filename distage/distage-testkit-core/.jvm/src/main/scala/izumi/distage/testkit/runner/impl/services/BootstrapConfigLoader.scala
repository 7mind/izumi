package izumi.distage.testkit.runner.impl.services

import izumi.distage.framework.services.{ConfigLoader, ConfigLoaderArgs, ConfigLocationProvider, ConfigMerger}
import izumi.logstage.api.IzLogger

private[services] object BootstrapConfigLoader {
  def apply(logger: IzLogger, merger: ConfigMerger, locationProvider: ConfigLocationProvider, configLoaderArgs: ConfigLoaderArgs): ConfigLoader = {
    new ConfigLoader.LocalFSImpl(logger, merger, locationProvider, configLoaderArgs)
  }
}
