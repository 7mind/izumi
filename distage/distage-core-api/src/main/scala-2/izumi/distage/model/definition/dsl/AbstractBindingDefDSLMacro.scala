package izumi.distage.model.definition.dsl

import izumi.distage.constructors.macros.MakeMacro

import scala.language.experimental.macros

trait AbstractBindingDefDSLMacro[BindDSL[_]] {
  final protected[this] def make[T]: BindDSL[T] = macro MakeMacro.make[BindDSL, T]
}
