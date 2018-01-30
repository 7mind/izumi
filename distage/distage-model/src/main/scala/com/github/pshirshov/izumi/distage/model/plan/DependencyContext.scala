package com.github.pshirshov.izumi.distage.model.plan

import com.github.pshirshov.izumi.distage.model.reflection.universe.{DIUniverseBase, RuntimeUniverse}

trait DependencyContext { this: DIUniverseBase =>

  sealed trait DependencyContext {
    def definingClass: TypeFull
  }

  object DependencyContext {

    import u._

    case class MethodContext(definingClass: TypeFull) extends DependencyContext

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
          { new ${symbolOf[RuntimeUniverse.type].asClass.module}.DependencyContext.ConstructorParameterContext($definingClass) }
          """
        }
      }

      case class MethodParameterContext(factoryClass: TypeFull, factoryMethod: MethodSymb) extends ParameterContext {
        override def definingClass: TypeFull = factoryClass
      }

      case class CallableParameterContext(definingCallable: Callable) extends ParameterContext {
        override def definingClass: TypeFull = definingCallable.ret
      }

  }

}
