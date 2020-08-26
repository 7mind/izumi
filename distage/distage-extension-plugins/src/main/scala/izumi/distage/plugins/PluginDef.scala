package izumi.distage.plugins

import izumi.distage.model.definition.ModuleDef
import izumi.fundamentals.platform.language.unused

abstract class PluginDef[T](implicit @unused ev: ForcedRecompilationToken[T]) extends PluginBase with ModuleDef
