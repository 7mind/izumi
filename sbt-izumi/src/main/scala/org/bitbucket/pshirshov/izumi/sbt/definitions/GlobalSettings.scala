package org.bitbucket.pshirshov.izumi.sbt.definitions

import org.bitbucket.pshirshov.izumi.sbt._
import sbt.librarymanagement.syntax
import sbt.{Defaults, Project}


trait GlobalSettings {
  def customSettings: Map[SettingsGroupId, ProjectSettings] = Map()

  def globalSettings: ProjectSettings = ProjectSettings.empty

  def settingsGroup(id: SettingsGroupId): ProjectSettings = settings.getOrElse(id, ProjectSettings.empty)

  def globalSettingsGroup: ProjectSettings = settingsGroup(SettingsGroupId.GlobalSettingsGroup)

  def rootSettingsGroup: ProjectSettings = settingsGroup(SettingsGroupId.RootSettingsGroup)

  protected def settings: Map[SettingsGroupId, ProjectSettings] = customSettings ++ Map(
    SettingsGroupId.GlobalSettingsGroup -> globalSettings
    , SettingsGroupId.RootSettingsGroup -> ProjectSettings(
      plugins = Set(
        BuildPlugin
        , CompilerOptionsPlugin
        , ResolverPlugin
        , TestingPlugin
      )
    )
    , SettingsGroupId.ItSettingsGroup -> ProjectSettings(
      settings = Seq(Defaults.itSettings, NestedTestScopesPlugin.itSettings).flatten
      , moreExtenders = { s =>
        Set(
          new Extender {
            override def extend(p: Project) = p.configs(syntax.IntegrationTest)
          }
        )
      }
    )
  )
}
