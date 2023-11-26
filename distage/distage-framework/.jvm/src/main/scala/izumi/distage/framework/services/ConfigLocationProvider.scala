package izumi.distage.framework.services

import izumi.distage.config.model.{ConfigSource, ResourceConfigKind}

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

  private def defaultBaseConfigs: Seq[String] = Seq("application", "common")

  private def defaultConfigReferences(name: String): Seq[ConfigSource] = {
    Seq(
      ConfigSource.Resource(s"$name.conf", ResourceConfigKind.Primary),
      ConfigSource.Resource(s"$name-reference.conf", ResourceConfigKind.Primary),
      ConfigSource.Resource(s"$name-reference-dev.conf", ResourceConfigKind.Development),
    )
  }
}
