package izumi.distage.plugins

import izumi.distage.model.definition.ModuleDef
import izumi.fundamentals.platform.language.unused

/**
  * Allows defining Plugins using [[izumi.distage.model.definition.dsl.ModuleDefDSL]] syntax
  *
  * {{{
  *   object MyPlugin extends PluginDef {
  *     include(myModule[F])
  *
  *     make[Xa[F]].from[Xa.Impl[F]]
  *   }
  * }}}
  *
  * @param recompilationToken Makes compile-time checks re-run when the source code of this `PluginDef` is changed, if it's used in the checked role.
  *
  * @see [[https://izumi.7mind.io/distage/distage-framework#compile-time-checks Compile-time checks]]
  */
abstract class PluginDef[T](implicit @unused recompilationToken: ForcedRecompilationToken[T]) extends PluginBase with ModuleDef
