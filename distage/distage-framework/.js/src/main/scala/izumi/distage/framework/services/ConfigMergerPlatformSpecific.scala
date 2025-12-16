package izumi.distage.framework.services

import izumi.distage.config.DistageConfigImpl
import izumi.logstage.api.IzLogger

import scala.annotation.unused

private[services] trait ConfigMergerPlatformSpecific {
  final def addSystemPropsImpl(config: DistageConfigImpl, @unused enableConfigEnvOverrides: Boolean, @unused logger: IzLogger): DistageConfigImpl = config
}
