package izumi.distage.plugins

import izumi.distage.model.definition.ModuleDef

abstract class PluginDef[T <: String](implicit ev: ForcedRecompilationToken[T]) extends PluginBase with ModuleDef {
  type RecompilationToken = T
}
