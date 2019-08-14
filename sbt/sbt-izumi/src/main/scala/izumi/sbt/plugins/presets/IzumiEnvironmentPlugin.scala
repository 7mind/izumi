package izumi.sbt.plugins.presets

import izumi.sbt.plugins
import sbt.{AutoPlugin, Plugins}

trait IzumiEnvironmentBase extends AutoPlugin {

  override def requires: Plugins = super.requires &&
    plugins.IzumiBuildManifestPlugin &&
    plugins.IzumiConvenienceTasksPlugin &&
    plugins.IzumiDslPlugin &&
    plugins.IzumiPropertiesPlugin &&
    plugins.IzumiResolverPlugin &&
    plugins.IzumiInheritedTestScopesPlugin
}

object IzumiEnvironmentPlugin extends IzumiEnvironmentBase
