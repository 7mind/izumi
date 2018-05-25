package com.github.pshirshov.izumi.sbt.definitions

import com.github.pshirshov.izumi.sbt.IzumiSettingsGroups.autoImport.SettingsGroupId
import com.github.pshirshov.izumi.sbt._
import sbt.librarymanagement.syntax
import sbt.{Defaults, Project}

object ItSettingsGroup extends AbstractSettingsGroup {
  override def id: SettingsGroupId = SettingsGroupId.ItSettingsGroup

  override def applyTo(p: Project) = {
    super
      .applyTo(p)
      .configs(syntax.IntegrationTest)
  }

  override def settings: Seq[sbt.Setting[_]] = {
    Seq(Defaults.itSettings, InheritedTestScopesPlugin.itSettings).flatten
  }

}

trait GlobalSettings {
  final def allSettings: Map[SettingsGroupId, AbstractSettingsGroup] = defaultSettings ++ settings

  def settings: Map[SettingsGroupId, AbstractSettingsGroup] = Map()

  final def defaultSettings: Map[SettingsGroupId, AbstractSettingsGroup] = {
    Map(
      SettingsGroupId.GlobalSettingsGroup -> SettingsGroupImpl.empty(SettingsGroupId.GlobalSettingsGroup)
      , SettingsGroupId.RootSettingsGroup -> SettingsGroupImpl.empty(SettingsGroupId.RootSettingsGroup)
      , SettingsGroupId.ItSettingsGroup -> ItSettingsGroup
    )
  }
}
