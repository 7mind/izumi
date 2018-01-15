package org.bitbucket.pshirshov.izumi.sbt.definitions

import org.bitbucket.pshirshov.izumi.sbt.IzumiSettingsGroups.autoImport.SettingsGroupId
import org.bitbucket.pshirshov.izumi.sbt._
import sbt.librarymanagement.syntax
import sbt.{Defaults, Project}


trait GlobalSettings {
  final def allSettings: Map[SettingsGroupId, ProjectSettings] = defaultSettings ++ settings

  protected def settings: Map[SettingsGroupId, ProjectSettings] = Map()

  protected def defaultSettings: Map[SettingsGroupId, ProjectSettings] = {
    Map(
      SettingsGroupId.GlobalSettingsGroup -> ProjectSettings.empty
      , SettingsGroupId.RootSettingsGroup -> ProjectSettings.empty
      , SettingsGroupId.ItSettingsGroup -> ProjectSettings(
        settings = Seq(Defaults.itSettings, InheritedTestScopesPlugin.itSettings).flatten
        , moreExtenders = {
          (self, existing) =>
            existing ++ Set(
              new Extender {
                override def extend(p: Project) = p.configs(syntax.IntegrationTest)
              }
            )
        }
      )
    )
  }
}
