package org.bitbucket.pshirshov.izumi.sbt.definitions

import sbt.{AutoPlugin, ModuleID, Plugins}
import sbt.librarymanagement.InclExclRule

case class ProjectSettings
(
  settings: Seq[sbt.Setting[_]] = Seq.empty
  , sharedDeps: Set[ModuleID] = Set.empty
  , exclusions: Set[InclExclRule] = Set.empty
  , plugins: Set[Plugins] = Set.empty
  , disabledPlugins: Set[AutoPlugin] = Set.empty
  , moreExtenders: (ProjectSettings, Set[Extender]) => Set[Extender] = (self, extenders) => extenders
) {
  def extenders: Set[Extender] = moreExtenders(this, Set(
    new GlobalSettingsExtender(this)
    , new SharedDepsExtender(this)
    , new GlobalExclusionsExtender(this)
    , new PluginsExtender(this)
  ))
}

object ProjectSettings {
  def empty: ProjectSettings = ProjectSettings()
}
