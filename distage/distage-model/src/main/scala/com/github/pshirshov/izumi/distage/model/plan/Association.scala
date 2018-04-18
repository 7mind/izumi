package com.github.pshirshov.izumi.distage.model.plan

import com.github.pshirshov.izumi.distage.model.references.DIKey
import com.github.pshirshov.izumi.distage.model.reflection.universe.{Callable, DIUniverseBase, RuntimeUniverse, SafeType}
import com.github.pshirshov.izumi.distage.model.util.Formattable

trait Association {
  this:  DIUniverseBase
    with SafeType
    with Callable
    with DIKey
    with DependencyContext
  =>

  sealed trait Association extends Formattable {
    def wireWith: DIKey
  }

  object Association {
    import u._

    case class Parameter(context: DependencyContext.ParameterContext, name: String, tpe: TypeFull, wireWith: DIKey) extends Association {
      override def format: String = s"""par $name: $tpe = lookup($wireWith)"""
    }
    object Parameter {
      implicit final val liftableParameter: Liftable[Parameter] = {
        case Parameter(context, name, tpe, wireWith) => q"""
        { new ${symbolOf[RuntimeUniverse.type].asClass.module}.Association.Parameter($context, $name, $tpe, $wireWith) }
        """
      }
    }

    case class Method(context: DependencyContext.MethodContext, symbol: Symb, wireWith: DIKey) extends Association {
      override def format: String = s"""def ${symbol.info.typeSymbol.name}: ${symbol.info.resultType} = lookup($wireWith)"""
    }

  }

}
