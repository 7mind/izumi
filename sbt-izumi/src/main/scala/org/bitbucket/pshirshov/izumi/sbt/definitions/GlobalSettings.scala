package org.bitbucket.pshirshov.izumi.sbt.definitions

import org.bitbucket.pshirshov.izumi.sbt._
import sbt.librarymanagement.syntax
import sbt.{Defaults, Project}


trait GlobalSettings {
//  def customSettings: Map[SettingsGroupId, ProjectSettings] = Map()

//  def globalSettings: ProjectSettings = ProjectSettings.empty

//  def settingsGroup(id: SettingsGroupId): ProjectSettings = settings.getOrElse(id, ProjectSettings.empty)
//
//  def globalSettingsGroup: ProjectSettings = settingsGroup(SettingsGroupId.GlobalSettingsGroup)
//
//  def rootSettingsGroup: ProjectSettings = settingsGroup(SettingsGroupId.RootSettingsGroup)

  def allSettings: Map[SettingsGroupId, ProjectSettings] = defaultSettings ++ settings

  protected def settings: Map[SettingsGroupId, ProjectSettings] = Map()

  protected def defaultSettings: Map[SettingsGroupId, ProjectSettings] = {
    Map(
      SettingsGroupId.GlobalSettingsGroup -> ProjectSettings.empty
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
