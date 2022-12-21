package izumi.distage.model.definition.dsl

import izumi.distage.constructors.macros.AnyConstructorMacro

import scala.language.experimental.macros

trait AbstractBindingDefDSLMacro[BindDSL[_]] {
  final protected[this] def make[T]: BindDSL[T] = macro AnyConstructorMacro.make[BindDSL, T]
}
