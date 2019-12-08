package izumi.distage.model.plan.operations

import izumi.distage.model.references.WithDIKey
import izumi.distage.model.reflection.universe.{DIUniverseBase, WithDISafeType, WithDISymbolInfo}

trait WithDIAssociation {
  this:  DIUniverseBase
    with WithDISafeType
    with WithDIKey
    with WithDISymbolInfo
  =>

  sealed trait Association {
    def symbol: SymbolInfo
    def key: DIKey.BasicKey
    def tpe: SafeType
    def name: String
    def isByName: Boolean

    def asParameter: Association.Parameter
    def withKey(key: DIKey.BasicKey): Association
  }

  object Association {
    case class Parameter(symbol: SymbolInfo, key: DIKey.BasicKey) extends Association {
      override final def name: String = symbol.name
      override final def tpe: SafeType = symbol.finalResultType
      override final def isByName: Boolean = symbol.isByName
      override final def withKey(key: DIKey.BasicKey): Association.Parameter = copy(key = key)
      override final def asParameter: Association.Parameter = this

      final def wasGeneric: Boolean = symbol.wasGeneric
    }

    // FIXME: non-existent wiring, at runtime there are only Parameters
    case class AbstractMethod(symbol: SymbolInfo, key: DIKey.BasicKey) extends Association {
      override final def name: String = symbol.name
      override final def tpe: SafeType = symbol.finalResultType
      override final def isByName: Boolean = true
      override final def withKey(key: DIKey.BasicKey): Association.AbstractMethod = copy(key = key)
      override final def asParameter: Parameter = Parameter(symbol.withIsByName(true), key)
    }
  }

}
