package izumi.distage.framework.services

import izumi.distage.framework.services.ConfigLoader.LocalFSImpl.{ConfigSource, ResourceConfigKind}

trait ConfigLocationProvider {
  def forRole(roleName: String): Seq[ConfigSource] = ConfigLocationProvider.defaultConfigReferences(roleName)

  def forBase(filename: String): Seq[ConfigSource] = ConfigLocationProvider.defaultConfigReferences(filename)

  def defaultBaseConfigs: Seq[String] = ConfigLocationProvider.defaultBaseConfigs
}

object ConfigLocationProvider {
  object Default extends ConfigLocationProvider

  def defaultBaseConfigs: Seq[String] = Seq("application", "common")

  def defaultConfigReferences(name: String): Seq[ConfigSource] = {
    Seq(
      ConfigSource.Resource(s"$name.conf", ResourceConfigKind.Primary),
      ConfigSource.Resource(s"$name-reference.conf", ResourceConfigKind.Primary),
      ConfigSource.Resource(s"$name-reference-dev.conf", ResourceConfigKind.Development),
    )
  }
}
