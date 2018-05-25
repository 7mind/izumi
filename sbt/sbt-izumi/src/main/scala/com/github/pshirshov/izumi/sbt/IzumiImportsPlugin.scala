package com.github.pshirshov.izumi.sbt

import sbt.AutoPlugin

object IzumiImportsPlugin extends AutoPlugin {
  override def trigger = allRequirements

  //noinspection TypeAnnotation
  object autoImport {
    val IzumiEnvironmentPlugin = com.github.pshirshov.izumi.sbt.IzumiEnvironmentPlugin

    val CompilerOptionsPlugin = com.github.pshirshov.izumi.sbt.CompilerOptionsPlugin
    val ConvenienceTasksPlugin = com.github.pshirshov.izumi.sbt.ConvenienceTasksPlugin
    val IzumiPropertiesPlugin = com.github.pshirshov.izumi.sbt.IzumiPropertiesPlugin
    val PublishingPlugin = com.github.pshirshov.izumi.sbt.PublishingPlugin
    val ResolverPlugin = com.github.pshirshov.izumi.sbt.ResolverPlugin

    val GitStampPlugin = com.github.pshirshov.izumi.sbt.GitStampPlugin

    val InheritedTestScopesPlugin = com.github.pshirshov.izumi.sbt.InheritedTestScopesPlugin

    val IzumiDslPlugin = com.github.pshirshov.izumi.sbt.IzumiDslPlugin
    val IzumiScopesPlugin = com.github.pshirshov.izumi.sbt.IzumiScopesPlugin
    val IzumiSettingsGroups = com.github.pshirshov.izumi.sbt.IzumiSettingsGroups

    type GlobalSettings = definitions.GlobalSettings
    type SettingsGroup = definitions.SettingsGroup
    type SettingsGroupId = IzumiSettingsGroups.autoImport.SettingsGroupId
  }

}








