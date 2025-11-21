package izumi.distage.plugins

import izumi.distage.model.definition.ModuleDef
import scala.annotation.unused

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
  * @note Since this is an abstract class, you cannot mix it with other classes or use it as a mixin.
  *       Instead, you may inherit from [[PluginBase]], which is a trait – but in that case compile-time checks will not
  *       re-run when the class is updated in that case. Alternatively, you may use [[izumi.distage.model.definition.dsl.IncludesDSL#include]]
  *       to compose modules as values instead of using inheritance.
  *
  * @param recompilationToken Makes compile-time checks re-run when the source code of this `PluginDef` is changed, if it's used in the checked role.
  *
  * @see [[https://izumi.7mind.io/distage/distage-framework#plugins Plugins]]
  * @see [[https://izumi.7mind.io/distage/distage-framework#compile-time-checks Compile-time checks]]
  */
abstract class PluginDef[T](implicit @unused recompilationToken: ForcedRecompilationToken[T]) extends PluginBase with ModuleDef
