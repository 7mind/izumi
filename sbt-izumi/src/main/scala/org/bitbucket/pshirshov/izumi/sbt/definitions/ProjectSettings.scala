package org.bitbucket.pshirshov.izumi.sbt.definitions

import sbt.{ModuleID, Plugins}
import sbt.librarymanagement.InclExclRule

case class ProjectSettings
(
  settings: Seq[sbt.Setting[_]] = Seq.empty
  , sharedDeps: Set[ModuleID] = Set.empty
  , exclusions: Set[InclExclRule] = Set.empty
  , plugins: Set[Plugins] = Set.empty
  , moreExtenders: ProjectSettings => Set[Extender] = (_) => Set.empty
) {
  def extenders: Set[Extender] = moreExtenders(this) ++ Set(
    new GlobalSettingsExtender(this)
    , new SharedDepsExtender(this)
    , new GlobalExclusionsExtender(this)
  )
}

object ProjectSettings {
  def empty: ProjectSettings = ProjectSettings()
}
