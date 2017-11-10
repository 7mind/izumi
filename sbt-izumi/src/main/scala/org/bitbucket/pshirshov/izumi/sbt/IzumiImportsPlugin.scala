package org.bitbucket.pshirshov.izumi.sbt

import sbt.AutoPlugin

object IzumiImportsPlugin extends AutoPlugin {
  override def trigger = allRequirements

  //noinspection TypeAnnotation
  object autoImport {
    val IzumiDsl = definitions.IzumiDsl
    val IzumiScopes = definitions.IzumiScopes
    val IzumiProperties = definitions.Properties

    val GitStampPlugin = org.bitbucket.pshirshov.izumi.sbt.GitStampPlugin
    val ConvenienceTasksPlugin = org.bitbucket.pshirshov.izumi.sbt.ConvenienceTasksPlugin

    type GlobalDefs = definitions.GlobalDefs
    type GlobalSettings = definitions.GlobalSettings
    type ProjectSettings = definitions.ProjectSettings
    type SettingsGroupId = definitions.SettingsGroupId
  }
}
