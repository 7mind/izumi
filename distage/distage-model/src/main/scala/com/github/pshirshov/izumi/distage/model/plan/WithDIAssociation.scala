package com.github.pshirshov.izumi.distage.model.plan

import com.github.pshirshov.izumi.distage.model.references.WithDIKey
import com.github.pshirshov.izumi.distage.model.reflection.universe._
import com.github.pshirshov.izumi.distage.model.util.{Format, Formattable}
import com.github.pshirshov.izumi.distage.model.util.Format._

trait WithDIAssociation {
  this:  DIUniverseBase
    with WithDISafeType
    with WithDICallable
    with WithDIKey
    with WithDIDependencyContext
    with WithDISymbolInfo
  =>

  sealed trait Association extends Formattable {
    def name: String
    def wireWith: DIKey
    def context: DependencyContext
  }

  object Association {
    case class Parameter(context: DependencyContext.ParameterContext, name: String, tpe: SafeType, wireWith: DIKey) extends Association {
      override def format: Format = {
        Format("par %s: %s = lookup(%s)", name, tpe, wireWith)
      }
    }

    case class AbstractMethod(context: DependencyContext.MethodContext, name: String, tpe: SafeType, wireWith: DIKey) extends Association {
      override def format: Format = {
        Format("def %s: %s = lookup($s)", name, tpe, wireWith)
      }
    }

    implicit class ParameterWithWireWith(p: Association.Parameter) {
      def withWireWith(key: DIKey): Association.Parameter =
        p.copy(wireWith = key)
    }

    implicit class MethodWithWireWith(m: Association.AbstractMethod) {
      def withWireWith(key: DIKey): Association.AbstractMethod =
        m.copy(wireWith = key)
    }
  }

}
