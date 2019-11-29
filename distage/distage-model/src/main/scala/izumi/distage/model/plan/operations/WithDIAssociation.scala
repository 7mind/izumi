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
    def name: String

    def withKey(key: DIKey.BasicKey): Association
  }

  object Association {
    case class Parameter(symbol: SymbolInfo, key: DIKey.BasicKey) extends Association {
      final def withKey(key: DIKey.BasicKey): Association.Parameter = copy(key = key)
      final def name: String = symbol.name
      final def tpe: SafeType = symbol.finalResultType
      final def isByName: Boolean = symbol.isByName
      final def wasGeneric: Boolean = symbol.wasGeneric
    }

    case class AbstractMethod(symbol: SymbolInfo, key: DIKey.BasicKey) extends Association {
      final def withKey(key: DIKey.BasicKey): Association.AbstractMethod = copy(key = key)
      final def name: String = symbol.name
      final def tpe: SafeType = symbol.finalResultType
      final def asParameter: Parameter = Parameter(symbol, key)
    }
  }

}
