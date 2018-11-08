package com.github.pshirshov.izumi.distage.model.plan

import com.github.pshirshov.izumi.distage.model.references.WithDIKey
import com.github.pshirshov.izumi.distage.model.reflection.universe._

trait WithDIAssociation {
  this:  DIUniverseBase
    with WithDISafeType
    with WithDICallable
    with WithDIKey
    with WithDIDependencyContext
    with WithDISymbolInfo
  =>

  sealed trait Association {
    def name: String
    def wireWith: DIKey.BasicKey
    def context: DependencyContext
    def format: String
  }

  object Association {
    case class Parameter(context: DependencyContext.ParameterContext, name: String, tpe: SafeType, wireWith: DIKey.BasicKey, isByName: Boolean, wasGeneric: Boolean) extends Association {
      override def format: String = s"""par $name: $tpe = lookup($wireWith)"""
    }

    case class AbstractMethod(context: DependencyContext.MethodContext, name: String, tpe: SafeType, wireWith: DIKey.BasicKey) extends Association {
      override def format: String = s"""def $name: $tpe = lookup($wireWith)"""
    }

    implicit class ParameterWithWireWith(p: Association.Parameter) {
      def withWireWith(key: DIKey.BasicKey): Association.Parameter =
        p.copy(wireWith = key)
    }

    implicit class MethodWithWireWith(m: Association.AbstractMethod) {
      def withWireWith(key: DIKey.BasicKey): Association.AbstractMethod =
        m.copy(wireWith = key)
    }
  }

}
