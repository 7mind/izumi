package izumi.distage.model.plan.operations

import izumi.distage.model.references.WithDIKey
import izumi.distage.model.reflection.universe.{DIUniverseBase, WithDICallable, WithDISafeType, WithDISymbolInfo}

trait WithDIAssociation {
  this:  DIUniverseBase
    with WithDISafeType
    with WithDICallable
    with WithDIKey
    with WithDISymbolInfo
  =>

  sealed trait Association {
    def symbol: SymbolInfo
    def key: DIKey.BasicKey
    def tpe: SafeType
    def name: String
    def isByName: Boolean

    def withKey(key: DIKey.BasicKey): Association
  }

  object Association {
    case class Parameter(symbol: SymbolInfo, key: DIKey.BasicKey) extends Association {
      override final def name: String = symbol.name
      override final def tpe: SafeType = symbol.finalResultType
      override final def isByName: Boolean = symbol.isByName
      override final def withKey(key: DIKey.BasicKey): Association.Parameter = copy(key = key)

      final def wasGeneric: Boolean = symbol.wasGeneric
    }

    // FIXME: non-existent wiring, at runtime there are only Parameters
    case class AbstractMethod(symbol: SymbolInfo, key: DIKey.BasicKey) extends Association {
      override final def name: String = symbol.name
      override final def tpe: SafeType = symbol.finalResultType
      override final def isByName: Boolean = true
      override final def withKey(key: DIKey.BasicKey): Association.AbstractMethod = copy(key = key)

      final def asParameter: Parameter = Parameter(symbol.withIsByName(true), key)
    }
  }

}
