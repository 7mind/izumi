package izumi.distage.plugins

import izumi.distage.model.definition.BootstrapModuleDef
import scala.annotation.unused

/** @see [[izumi.distage.plugins.PluginDef]] */
abstract class BootstrapPluginDef(implicit @unused recompilationToken: ForcedRecompilationToken[?]) extends BootstrapPlugin with BootstrapModuleDef
