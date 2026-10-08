package izumi.distage.framework.services

import izumi.distage.config.DistageConfigImpl
import izumi.logstage.api.IzLogger

import scala.annotation.unused

private[services] trait ConfigMergerPlatformSpecific {
  final def addSystemPropsImpl(config: DistageConfigImpl, @unused enableConfigEnvOverrides: Boolean, logger: IzLogger): DistageConfigImpl = {
    logger.debug("Automatic system property and CONFIG_FORCE_ overrides are unavailable with Native JSON configuration")
    config
  }
}
