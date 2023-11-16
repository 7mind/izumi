package izumi.distage.testkit.runner.impl.services

import izumi.distage.config.model.AppConfig
import izumi.distage.framework.services.{ConfigArgsProvider, ConfigLoader, ConfigLocationProvider}
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
            val configLoader = makeConfigLoader(env.configBaseName, envLogger)
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

    protected def makeConfigLoader(configBaseName: String, logger: IzLogger): ConfigLoader = {
      val provider = new ConfigArgsProvider {
        override def args(): ConfigLoader.Args = ConfigLoader.Args(None, Map(configBaseName -> None))
      }
      new ConfigLoader.LocalFSImpl(logger, ConfigLocationProvider.Default, provider)
    }

  }
}
