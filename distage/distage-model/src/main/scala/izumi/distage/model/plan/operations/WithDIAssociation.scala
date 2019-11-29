package izumi.distage.model.plan.operations

import izumi.distage.model.references.WithDIKey
import izumi.distage.model.reflection.universe.{DIUniverseBase, WithDICallable, WithDISafeType, WithDISymbolInfo}

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
  }

  object Association {
    case class Parameter(context: DependencyContext.ParameterContext, name: String, tpe: SafeType, wireWith: DIKey.BasicKey, isByName: Boolean, wasGeneric: Boolean) extends Association {
      final def withWireWith(key: DIKey.BasicKey): Association.Parameter = copy(wireWith = key)
    }

    case class AbstractMethod(context: DependencyContext.MethodContext, name: String, tpe: SafeType, wireWith: DIKey.BasicKey) extends Association {
      final def withWireWith(key: DIKey.BasicKey): Association.AbstractMethod = copy(wireWith = key)
    }
  }

}
