package izumi.distage.framework.services

import com.typesafe.config.{Config, ConfigFactory}
import izumi.logstage.api.IzLogger
import izumi.logstage.api.Log.Message
import logstage.Info

private[services] trait ConfigMergerPlatformSpecific {

  final def addSystemPropsImpl(config: Config, enableConfigEnvOverrides: Boolean, logger: IzLogger): Config = {
    val envOverridesConfig = if (enableConfigEnvOverrides) {
      ConfigFactory.systemEnvironmentOverrides()
    } else {
      ConfigFactory.empty()
    }
    val sysPropsConfig = ConfigFactory.systemProperties()
    val result = envOverridesConfig
      .withFallback(sysPropsConfig)
      .withFallback(config)
      .resolve()

    logger.log(Info)(
      Message(s"Config with ${config.entrySet().size() -> "keys"} has been enhanced with ") ++
      (if (enableConfigEnvOverrides) Message(s"${envOverridesConfig.entrySet().size() -> "environment variable overrides"} and ") else Message.empty) ++
      Message(s"${sysPropsConfig.entrySet().size() -> "system properties"}, new config has ${result.entrySet().size() -> "new keys"}")
    )

    result
  }

}
