package izumi.sbt.plugins.global

import izumi.sbt.{definitions, plugins}
import sbt.AutoPlugin

object IzumiImportsPlugin extends AutoPlugin {
  override def trigger = allRequirements

  //noinspection TypeAnnotation
  object autoImport {
    val IzumiEnvironmentPlugin = plugins.presets.IzumiEnvironmentPlugin
    val IzumiGitEnvironmentPlugin = plugins.presets.IzumiGitEnvironmentPlugin

    val IzumiInheritedTestScopesPlugin = plugins.IzumiInheritedTestScopesPlugin

    val IzumiGitStampPlugin = plugins.IzumiGitStampPlugin

    val IzumiBuildManifestPlugin = plugins.IzumiBuildManifestPlugin
    val IzumiConvenienceTasksPlugin = plugins.IzumiConvenienceTasksPlugin
    val IzumiDslPlugin = plugins.IzumiDslPlugin
    val IzumiPropertiesPlugin = plugins.IzumiPropertiesPlugin
    val IzumiResolverPlugin = plugins.IzumiResolverPlugin
    val IzumiScopesPlugin = plugins.IzumiInheritedTestScopesPlugin

    val IzumiCompilerOptionsPlugin = plugins.optional.IzumiCompilerOptionsPlugin
    val IzumiExposedTestScopesPlugin = plugins.optional.IzumiExposedTestScopesPlugin
    val IzumiFetchPlugin = plugins.optional.IzumiFetchPlugin
    val IzumiPublishingPlugin =  plugins.optional.IzumiPublishingPlugin
    val IzumiTestPublishingPlugin =  plugins.optional.IzumiTestPublishingPlugin

    type SettingsGroup = definitions.SettingsGroup
    type SettingsGroupId = definitions.SettingsGroupId
    val SettingsGroupId =  definitions.SettingsGroupId

    type DefaultGlobalSettingsGroup = definitions.DefaultGlobalSettingsGroup
  }

}








