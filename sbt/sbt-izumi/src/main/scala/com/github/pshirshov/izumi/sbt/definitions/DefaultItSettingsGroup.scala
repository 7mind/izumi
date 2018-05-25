package com.github.pshirshov.izumi.sbt.definitions

import com.github.pshirshov.izumi.sbt._
import sbt.librarymanagement.syntax
import sbt.{Defaults, Project}

object DefaultItSettingsGroup extends AbstractSettingsGroup {
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
