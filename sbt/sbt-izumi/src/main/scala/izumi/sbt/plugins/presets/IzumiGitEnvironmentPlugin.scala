package com.github.pshirshov.izumi.sbt.plugins.presets

import com.github.pshirshov.izumi.sbt.plugins
import sbt.Plugins

object IzumiGitEnvironmentPlugin extends IzumiEnvironmentBase {
  override def requires: Plugins = super.requires && plugins.IzumiGitStampPlugin
}
