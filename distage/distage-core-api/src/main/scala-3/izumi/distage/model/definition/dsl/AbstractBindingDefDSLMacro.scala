package izumi.distage.model.definition.dsl

import izumi.distage.constructors.FactoryConstructor
import izumi.distage.model.definition.Bindings
import izumi.distage.model.definition.dsl.AbstractBindingDefDSL.SingletonRef
import izumi.fundamentals.platform.language.CodePositionMaterializer
import izumi.reflect.Tag

trait AbstractBindingDefDSLMacro[BindDSL[_], BindDSLAfterFrom[_], SetDSL[_]] { this: AbstractBindingDefDSL[BindDSL, BindDSLAfterFrom, SetDSL] =>

  final protected[this] def make[T]: BindDSL[T] = ???

}
