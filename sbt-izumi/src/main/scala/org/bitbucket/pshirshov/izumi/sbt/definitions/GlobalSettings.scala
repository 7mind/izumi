package org.bitbucket.pshirshov.izumi.sbt.definitions

import org.bitbucket.pshirshov.izumi.sbt._
import sbt.Plugins


trait GlobalSettings {
  protected def customSettings: Map[SettingsGroupId, ProjectSettings] = Map()

  protected def globalSettings: ProjectSettings = ProjectSettings.empty

  private final def settings: Map[SettingsGroupId, ProjectSettings] = customSettings ++ Map(
    SettingsGroupId.GlobalSettingsGroup -> globalSettings
  )

  def settingsGroup(id: SettingsGroupId): ProjectSettings = settings.getOrElse(id, ProjectSettings.empty)
  def globalSettingsGroup: ProjectSettings = settingsGroup(SettingsGroupId.GlobalSettingsGroup)


  def rootPlugins: Set[Plugins] = Set(
    BuildPlugin
    , CompilerOptionsPlugin
    , ResolverPlugin
    , TestingPlugin
  )
}
