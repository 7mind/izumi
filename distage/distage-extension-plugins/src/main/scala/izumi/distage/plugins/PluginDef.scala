package izumi.distage.plugins

import izumi.distage.model.definition.ModuleDef
import izumi.fundamentals.platform.language.unused

/**
  * Use it to define Plugins using [[izumi.distage.model.definition.dsl.ModuleDefDSL]] syntax
  *
  * {{{
  *   object MyPlugin extends PluginDef {
  *     include(myModule[F])
  *
  *     make[Xa[F]].from[Xa.Impl[F]]
  *   }
  * }}}
  *
  * NOTE: Since this is an abstract class, you cannot mix it with other classes or use it as a mixin.
  *       You may inherit from [[PluginBase]], which is a trait, instead – but compile-time will not re-run if the class is updated then.
  *       Alternatively, you may use [[izumi.distage.model.definition.dsl.IncludesDSL#include]] method to compose
  *       modules as values instead of using inheritance.
  *
  * @param recompilationToken Makes compile-time checks re-run when the source code of this `PluginDef` is changed, if it's used in the checked role.
  *
  * @see [[https://izumi.7mind.io/distage/distage-framework#plugins Plugins]]
  * @see [[https://izumi.7mind.io/distage/distage-framework#compile-time-checks Compile-time checks]]
  */
abstract class PluginDef[T](implicit @unused recompilationToken: ForcedRecompilationToken[T]) extends PluginBase with ModuleDef
