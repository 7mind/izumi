package org.bitbucket.pshirshov.izumi.sbt

import sbt.AutoPlugin

object IzumiImportsPlugin extends AutoPlugin {
  override def trigger = allRequirements

  //noinspection TypeAnnotation
  object autoImport {
    val IzumiEnvironmentPlugin = org.bitbucket.pshirshov.izumi.sbt.BuildPlugin

    val BuildPlugin = org.bitbucket.pshirshov.izumi.sbt.BuildPlugin
    val CompilerOptionsPlugin = org.bitbucket.pshirshov.izumi.sbt.CompilerOptionsPlugin
    val ConvenienceTasksPlugin = org.bitbucket.pshirshov.izumi.sbt.ConvenienceTasksPlugin
    val IzumiPropertiesPlugin = org.bitbucket.pshirshov.izumi.sbt.IzumiPropertiesPlugin
    val PublishingPlugin = org.bitbucket.pshirshov.izumi.sbt.PublishingPlugin
    val ResolverPlugin = org.bitbucket.pshirshov.izumi.sbt.ResolverPlugin
    val TestingPlugin = org.bitbucket.pshirshov.izumi.sbt.TestingPlugin
    
    val GitStampPlugin = org.bitbucket.pshirshov.izumi.sbt.GitStampPlugin

    val InheritedTestScopesPlugin = org.bitbucket.pshirshov.izumi.sbt.InheritedTestScopesPlugin

    val IzumiDslPlugin = org.bitbucket.pshirshov.izumi.sbt.IzumiDslPlugin
    val IzumiScopesPlugin = org.bitbucket.pshirshov.izumi.sbt.IzumiScopesPlugin
    val IzumiSettingsGroups = org.bitbucket.pshirshov.izumi.sbt.IzumiSettingsGroups

    type GlobalSettings = definitions.GlobalSettings
    type ProjectSettings = definitions.ProjectSettings
    type SettingsGroupId = IzumiSettingsGroups.autoImport.SettingsGroupId
  }

}








