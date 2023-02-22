package izumi.distage.testkit.runner.services

import izumi.distage.config.model.AppConfig
import izumi.distage.testkit.model.TestEnvironment
import izumi.logstage.api.IzLogger

import java.util.concurrent.ConcurrentHashMap

trait TestConfigLoader {
  def loadConfig(env: TestEnvironment, envLogger: IzLogger): AppConfig
}

object TestConfigLoader {
  private final val memoizedConfig = new ConcurrentHashMap[(String, BootstrapFactory, Option[AppConfig]), AppConfig]

  class TestConfigLoaderImpl() extends TestConfigLoader {
    def loadConfig(env: TestEnvironment, envLogger: IzLogger): AppConfig = {
      TestConfigLoader.memoizedConfig
        .computeIfAbsent(
          (env.configBaseName, env.bootstrapFactory, env.configOverrides),
          _ => {
            val configLoader = env.bootstrapFactory
              .makeConfigLoader(env.configBaseName, envLogger)
              .map {
                appConfig =>
                  env.configOverrides match {
                    case Some(overrides) =>
                      AppConfig(overrides.config.withFallback(appConfig.config).resolve())
                    case None =>
                      appConfig
                  }
              }
            configLoader.loadConfig()
          },
        )
    }
  }
}
