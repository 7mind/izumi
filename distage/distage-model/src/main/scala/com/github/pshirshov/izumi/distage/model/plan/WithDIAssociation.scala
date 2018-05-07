package com.github.pshirshov.izumi.distage.model.plan

import com.github.pshirshov.izumi.distage.model.references.WithDIKey
import com.github.pshirshov.izumi.distage.model.reflection.universe._
import com.github.pshirshov.izumi.distage.model.util.Formattable

trait WithDIAssociation {
  this:  DIUniverseBase
    with WithDISafeType
    with WithDICallable
    with WithDIKey
    with WithDIDependencyContext
  =>

  sealed trait Association extends Formattable {
    def wireWith: DIKey
  }

  object Association {
    @deprecated
    case class ExtendedParameter(symb: Symb, parameter: Parameter) {
      val wireWith: DIKey = parameter.wireWith
      val name: String = parameter.name
      val context: DependencyContext.ParameterContext = parameter.context
    }
    case class Parameter(context: DependencyContext.ParameterContext, name: String, wireWith: DIKey) extends Association {
      override def format: String = s"""par $name: ${wireWith.symbol} = lookup($wireWith)"""
    }

    object Parameter {
      def fromDIKey(context: DependencyContext.ParameterContext, key: DIKey): Parameter = {
        val name = key.symbol.tpe.typeSymbol.name.decodedName.toString

        Association.Parameter(context, name, key)
      }
    }

    case class AbstractMethod(context: DependencyContext.MethodContext, symbol: Symb, wireWith: DIKey) extends Association {
      override def format: String = s"""def ${symbol.info.typeSymbol.name}: ${symbol.info.resultType} = lookup($wireWith)"""
    }

  }

}
