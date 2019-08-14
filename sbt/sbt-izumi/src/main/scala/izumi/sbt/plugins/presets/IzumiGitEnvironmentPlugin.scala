package izumi.sbt.plugins.presets

import izumi.sbt.plugins
import sbt.Plugins

object IzumiGitEnvironmentPlugin extends IzumiEnvironmentBase {
  override def requires: Plugins = super.requires && plugins.IzumiGitStampPlugin
}
