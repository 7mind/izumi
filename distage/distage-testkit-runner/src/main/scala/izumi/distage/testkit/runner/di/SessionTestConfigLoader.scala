package izumi.distage.testkit.runner.di

import izumi.distage.config.model.AppConfig
import izumi.distage.testkit.model.TestEnvironment
import izumi.distage.testkit.runner.impl.services.TestConfigLoader
import izumi.fundamentals.platform.cache.SyncCache
import izumi.logstage.api.IzLogger

private[di] final class SessionTestConfigLoader(delegate: TestConfigLoader) extends TestConfigLoader {
  private val snapshots = new SyncCache[SessionTestConfigLoader.EnvironmentIdentity, AppConfig]

  override def loadConfig(environment: TestEnvironment, logger: IzLogger): AppConfig = {
    snapshots.getOrCompute(new SessionTestConfigLoader.EnvironmentIdentity(environment), delegate.loadConfig(environment, logger))
  }

  def retain(environment: TestEnvironment, config: AppConfig): Unit = snapshots.put(new SessionTestConfigLoader.EnvironmentIdentity(environment), config)
}

private[di] object SessionTestConfigLoader {
  final class EnvironmentIdentity(val value: TestEnvironment) {
    override def equals(other: Any): Boolean = other match {
      case that: EnvironmentIdentity => value.eq(that.value)
      case _ => false
    }
    override def hashCode(): Int = System.identityHashCode(value)
  }
}
