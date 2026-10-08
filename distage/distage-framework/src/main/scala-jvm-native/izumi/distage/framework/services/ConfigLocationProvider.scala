package izumi.distage.framework.services

import izumi.distage.config.model.ConfigSource

trait ConfigLocationProvider {
  def forRole(roleName: String): Seq[ConfigSource]

  def commonReferenceConfigs: Seq[ConfigSource]
}

object ConfigLocationProvider extends ConfigLocationProviderPlatformSpecific {
  object Default extends ConfigLocationProvider {
    def forRole(roleName: String): Seq[ConfigSource] = {
      ConfigLocationProvider.defaultConfigReferences(roleName)
    }

    def commonReferenceConfigs: Seq[ConfigSource] = {
      ConfigLocationProvider.defaultBaseConfigs.flatMap(ConfigLocationProvider.defaultConfigReferences)
    }
  }

  /** highest priority first, `application` overrides `common` */
  private def defaultBaseConfigs: Seq[String] = Seq("application", "common")

  /** highest priority first, `x.<extension>` overrides `x-reference.<extension>` overrides `x-reference-dev.<extension>` */
  private def defaultConfigReferences(name: String): Seq[ConfigSource] = {
    Seq(
      ConfigSource.Resource(s"$name.$configExtension"),
      ConfigSource.Resource(s"$name-reference.$configExtension"),
      ConfigSource.Resource(s"$name-reference-dev.$configExtension"),
    )
  }
}
