package izumi.distage.testkit.runner.di

import izumi.distage.framework.services.ConfigLocationProvider

private[di] final class SessionBootstrapFactory extends SessionBootstrapFactoryBase {
  override protected def makeConfigLocationProvider(configBaseName: String): ConfigLocationProvider = ConfigLocationProvider.Default
}
