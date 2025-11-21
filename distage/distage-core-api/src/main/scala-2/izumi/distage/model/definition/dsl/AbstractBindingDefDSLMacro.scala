package izumi.distage.model.definition.dsl

import izumi.distage.reflection.macros.constructors.MakeMacro
import scala.language.experimental.macros

trait AbstractBindingDefDSLMacro[BindDSL[_]] {
  final protected def make[T]: BindDSL[T] = macro MakeMacro.make[BindDSL, T]
}
