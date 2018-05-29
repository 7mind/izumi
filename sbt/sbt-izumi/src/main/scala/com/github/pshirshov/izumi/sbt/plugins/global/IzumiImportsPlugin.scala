package com.github.pshirshov.izumi.sbt.plugins.global

import com.github.pshirshov.izumi.sbt.{definitions, plugins}
import sbt.AutoPlugin

object IzumiImportsPlugin extends AutoPlugin {
  override def trigger = allRequirements

  //noinspection TypeAnnotation
  object autoImport {
    val IzumiEnvironmentPlugin = plugins.presets.IzumiEnvironmentPlugin

    val IzumiBuildManifestPlugin = plugins.IzumiBuildManifestPlugin
    val IzumiConvenienceTasksPlugin = plugins.IzumiConvenienceTasksPlugin
    val IzumiDslPlugin = plugins.IzumiDslPlugin
    val IzumiGitStampPlugin = plugins.IzumiGitStampPlugin
    val IzumiInheritedTestScopesPlugin = plugins.IzumiInheritedTestScopesPlugin
    val IzumiPropertiesPlugin = plugins.IzumiPropertiesPlugin
    val IzumiPublishingPlugin = plugins.IzumiPublishingPlugin
    val IzumiResolverPlugin = plugins.IzumiResolverPlugin
    val IzumiScopesPlugin = plugins.IzumiScopesPlugin

    val IzumiCompilerOptionsPlugin = plugins.optional.IzumiCompilerOptionsPlugin
    val IzumiFetchPlugin = plugins.optional.IzumiFetchPlugin

    type SettingsGroup = definitions.SettingsGroup
  }

}








