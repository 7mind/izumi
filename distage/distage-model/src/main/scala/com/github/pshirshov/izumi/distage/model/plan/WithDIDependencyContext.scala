package com.github.pshirshov.izumi.distage.model.plan

import com.github.pshirshov.izumi.distage.model.reflection.universe._

trait WithDIDependencyContext {
  this: DIUniverseBase
    with WithDISafeType
    with WithDICallable
    with DILiftableRuntimeUniverse
  =>

  sealed trait DependencyContext {
    def definingClass: TypeFull
  }

  object DependencyContext {

    import u._

    case class MethodContext(definingClass: TypeFull) extends DependencyContext

    case class FactoryMethodContext(factoryClass: TypeFull) extends DependencyContext {
      override def definingClass: TypeFull = factoryClass
    }

    sealed trait ParameterContext extends DependencyContext

    object ParameterContext {
      implicit final val liftableParameter: Liftable[ParameterContext] = {
        case c: ConstructorParameterContext => ConstructorParameterContext.liftableParameter(c)
        case c => throw new UnsupportedOperationException(s"Cannot lift DependencyContext from compile-time to runtime" +
          s": Only ConstructorParameterContext can be lifted, when resolving Liftable for $c")
      }
    }

    case class ConstructorParameterContext(definingClass: TypeFull) extends ParameterContext

    object ConstructorParameterContext {
      implicit final val liftableParameter: Liftable[ConstructorParameterContext] = {
        case ConstructorParameterContext(definingClass) => q"""
          { new $RuntimeDIUniverse.DependencyContext.ConstructorParameterContext($definingClass) }
          """
      }
    }

    case class MethodParameterContext(factoryClass: TypeFull, factoryMethod: MethodSymb) extends ParameterContext {
      override def definingClass: TypeFull = factoryClass
    }

    case class CallableParameterContext(definingCallable: Provider) extends ParameterContext {
      override def definingClass: TypeFull = definingCallable.ret
    }

  }

}
