package com.github.pshirshov.izumi.sbt.plugins.presets

import com.github.pshirshov.izumi.sbt.plugins
import sbt.{AutoPlugin, Plugins}

object IzumiEnvironmentPlugin extends AutoPlugin {

  override def requires: Plugins = super.requires &&
    plugins.IzumiBuildManifestPlugin &&
    plugins.IzumiConvenienceTasksPlugin &&
    plugins.IzumiDslPlugin &&
    plugins.IzumiGitStampPlugin &&
    plugins.IzumiInheritedTestScopesPlugin &&
    plugins.IzumiPropertiesPlugin &&
    plugins.IzumiPublishingPlugin &&
    plugins.IzumiResolverPlugin &&
    plugins.IzumiScopesPlugin
}
