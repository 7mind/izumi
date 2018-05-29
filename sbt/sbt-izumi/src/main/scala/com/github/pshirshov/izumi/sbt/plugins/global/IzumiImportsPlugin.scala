package com.github.pshirshov.izumi.sbt.plugins.global

import com.github.pshirshov.izumi.sbt.{definitions, plugins}
import sbt.AutoPlugin

object IzumiImportsPlugin extends AutoPlugin {
  override def trigger = allRequirements

  //noinspection TypeAnnotation
  object autoImport {
    val IzumiEnvironmentPlugin = plugins.presets.IzumiEnvironmentPlugin
    val IzumiGitEnvironmentPlugin = plugins.presets.IzumiGitEnvironmentPlugin

    val IzumiInheritedTestScopesPlugin = plugins.global.IzumiInheritedTestScopesPlugin

    val IzumiGitStampPlugin = plugins.IzumiGitStampPlugin

    val IzumiBuildManifestPlugin = plugins.IzumiBuildManifestPlugin
    val IzumiConvenienceTasksPlugin = plugins.IzumiConvenienceTasksPlugin
    val IzumiDslPlugin = plugins.IzumiDslPlugin
    val IzumiPropertiesPlugin = plugins.IzumiPropertiesPlugin
    val IzumiResolverPlugin = plugins.IzumiResolverPlugin
    val IzumiScopesPlugin = plugins.IzumiScopesPlugin

    val IzumiCompilerOptionsPlugin = plugins.optional.IzumiCompilerOptionsPlugin
    val IzumiPublishingPlugin =  plugins.optional.IzumiPublishingPlugin
    val IzumiFetchPlugin = plugins.optional.IzumiFetchPlugin

    type SettingsGroup = definitions.SettingsGroup
  }

}








