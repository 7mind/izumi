package izumi.distage.testkit.runner.impl.services

import izumi.distage.config.model.AppConfig
import izumi.distage.testkit.model.TestEnvironment
import izumi.logstage.api.IzLogger

import java.util.concurrent.ConcurrentHashMap

trait TestConfigLoader {
  def loadConfig(env: TestEnvironment, envLogger: IzLogger): AppConfig
}

object TestConfigLoader {

  class TestConfigLoaderImpl() extends TestConfigLoader {
    private final val memoizedConfig = new ConcurrentHashMap[(String, BootstrapFactory, Option[AppConfig]), AppConfig]

    def loadConfig(env: TestEnvironment, envLogger: IzLogger): AppConfig = {
      memoizedConfig
        .computeIfAbsent(
          (env.configBaseName, env.bootstrapFactory, env.configOverrides),
          _ => {
            val configLoader = env.bootstrapFactory
              .makeConfigLoader(env.configBaseName, envLogger)
              .map {
                appConfig =>
                  env.configOverrides match {
                    case Some(overrides) =>
                      AppConfig.provided(overrides.config.withFallback(appConfig.config).resolve())
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
