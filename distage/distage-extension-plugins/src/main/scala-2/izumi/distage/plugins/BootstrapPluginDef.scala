package izumi.distage.plugins

import izumi.distage.model.definition.BootstrapModuleDef
import scala.annotation.unused

/** @see [[izumi.distage.plugins.PluginDef]] */
abstract class BootstrapPluginDef[T](implicit @unused recompilationToken: ForcedRecompilationToken[T]) extends BootstrapPlugin with BootstrapModuleDef
