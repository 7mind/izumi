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
  * @param recompilationToken Forces compile-time checks to re-run when the source code of a (relevant) class inheriting `PluginDef` is changed.
  *
  * @see [[https://izumi.7mind.io/distage/distage-framework#compile-time-checks Compile-time checks]]
  */
abstract class PluginDef[T](implicit @unused recompilationToken: ForcedRecompilationToken[T]) extends PluginBase with ModuleDef
