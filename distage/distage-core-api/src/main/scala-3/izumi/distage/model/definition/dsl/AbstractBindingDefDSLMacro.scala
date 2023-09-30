package izumi.distage.model.definition.dsl

import izumi.distage.constructors.AnyConstructorMacro

trait AbstractBindingDefDSLMacro[BindDSL[_]] {
  inline final protected[this] def make[T]: BindDSL[T] = ${ AnyConstructorMacro.makeMethod[T, BindDSL[T]] }
}
