package izumi.distage.framework.services

import izumi.distage.config.model.ConfigSource

trait ConfigLocationProvider {
  def forRole(roleName: String): Seq[ConfigSource]

  def commonReferenceConfigs: Seq[ConfigSource]
}

object ConfigLocationProvider {
  object Default extends ConfigLocationProvider {
    def forRole(roleName: String): Seq[ConfigSource] = {
      ConfigLocationProvider.defaultConfigReferences(roleName)
    }

    def commonReferenceConfigs: Seq[ConfigSource] = {
      ConfigLocationProvider.defaultBaseConfigs.flatMap(ConfigLocationProvider.defaultConfigReferences)
    }
  }

  // ascending priority. application overrides common
  private def defaultBaseConfigs: Seq[String] = Seq("application", "common")

  private def defaultConfigReferences(name: String): Seq[ConfigSource] = {
    // highest priority first, x.conf overrides x-reference.conf
    Seq(
      ConfigSource.Resource(s"$name.conf"),
      ConfigSource.Resource(s"$name-reference.conf"),
      ConfigSource.Resource(s"$name-reference-dev.conf"),
    )
  }
}
