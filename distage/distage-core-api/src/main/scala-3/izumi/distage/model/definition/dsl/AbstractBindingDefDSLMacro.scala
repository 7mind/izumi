package izumi.distage.model.definition.dsl

import izumi.distage.constructors.AnyConstructorMacro

trait AbstractBindingDefDSLMacro[BindDSL[_], BindDSLAfterFrom[_], SetDSL[_]] { this: AbstractBindingDefDSL[BindDSL, BindDSLAfterFrom, SetDSL] =>
  inline final protected[this] def make[T]: BindDSL[T] = ${ AnyConstructorMacro.makeMethod[T, BindDSL[T]] }
}
