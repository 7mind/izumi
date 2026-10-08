package izumi.distage.testkit.runner.impl.services

import izumi.distage.framework.services.ConfigLoader
import izumi.logstage.api.IzLogger

trait BootstrapFactoryPlatformSpecific

private[services] trait DefaultBootstrapFactoryConfig extends BootstrapFactory {
  override def makeConfigLoader(configBaseName: String, logger: IzLogger): ConfigLoader = {
    // On Scala.js, we don't have file system access, so we use an empty config loader
    // Users can provide config via TestConfig.configOverrides
    ConfigLoader.empty
  }
}
