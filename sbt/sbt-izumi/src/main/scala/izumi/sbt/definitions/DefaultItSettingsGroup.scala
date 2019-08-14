package izumi.sbt.definitions

import izumi.sbt.plugins.optional.IzumiExposedTestScopesPlugin
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
    Seq(Defaults.itSettings, IzumiExposedTestScopesPlugin.itSettings).flatten
  }

}
