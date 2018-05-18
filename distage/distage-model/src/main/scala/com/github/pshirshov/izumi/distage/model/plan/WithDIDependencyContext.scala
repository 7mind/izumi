package com.github.pshirshov.izumi.distage.model.plan

import com.github.pshirshov.izumi.distage.model.reflection.universe._

trait WithDIDependencyContext {
  this: DIUniverseBase
    with WithDISafeType
    with WithDICallable =>

  sealed trait DependencyContext {
    def definingClass: TypeFull
  }

  object DependencyContext {

    case class MethodContext(definingClass: TypeFull) extends DependencyContext

    case class FactoryMethodContext(factoryClass: TypeFull) extends DependencyContext {
      override def definingClass: TypeFull = factoryClass
    }

    sealed trait ParameterContext extends DependencyContext

    case class ConstructorParameterContext(symb: Symb, definingClass: TypeFull) extends ParameterContext

    case class MethodParameterContext(factoryClass: TypeFull, factoryMethod: MethodSymb) extends ParameterContext {
      override def definingClass: TypeFull = factoryClass
    }

    case class CallableParameterContext(definingCallable: Provider) extends ParameterContext {
      override def definingClass: TypeFull = definingCallable.ret
    }

  }

}
