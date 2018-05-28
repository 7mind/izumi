package com.github.pshirshov.izumi.sbt

import sbt.{AutoPlugin, Plugins}

object IzumiEnvironmentPlugin extends AutoPlugin {

  override def requires: Plugins = super.requires &&
    InheritedTestScopesPlugin &&
    ConvenienceTasksPlugin &&
    IzumiPropertiesPlugin &&
    PublishingPlugin &&
    ResolverPlugin
}
