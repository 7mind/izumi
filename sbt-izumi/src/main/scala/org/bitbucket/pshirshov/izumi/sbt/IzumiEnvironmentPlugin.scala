package org.bitbucket.pshirshov.izumi.sbt

import sbt.{AutoPlugin, Plugins}

object IzumiEnvironmentPlugin extends AutoPlugin {

  override def requires: Plugins = super.requires &&
    PublishingPlugin &&
    InheritedTestScopesPlugin &&
    CompilerOptionsPlugin &&
    ConvenienceTasksPlugin &&
    IzumiPropertiesPlugin &&
    ResolverPlugin
}
