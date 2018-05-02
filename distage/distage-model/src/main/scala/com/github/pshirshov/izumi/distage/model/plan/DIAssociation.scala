package com.github.pshirshov.izumi.distage.model.plan

import com.github.pshirshov.izumi.distage.model.references.DIKey
import com.github.pshirshov.izumi.distage.model.reflection.universe._
import com.github.pshirshov.izumi.distage.model.util.Formattable

trait DIAssociation {
  this:  DIUniverseBase
    with DISafeType
    with DICallable
    with DIKey
    with DIDependencyContext
    with DILiftableRuntimeUniverse
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
        { new $RuntimeDIUniverse.Association.Parameter($context, $name, $tpe, $wireWith) }
        """
      }
    }

    case class Method(context: DependencyContext.MethodContext, symbol: Symb, wireWith: DIKey) extends Association {
      override def format: String = s"""def ${symbol.info.typeSymbol.name}: ${symbol.info.resultType} = lookup($wireWith)"""
    }

  }

}
