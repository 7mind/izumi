package izumi.distage.model.reflection.universe

private[distage] trait WithDIAssociation {
  this:  DIUniverseBase
    with WithDISafeType
    with WithDIKey
    with WithDISymbolInfo
  =>

  sealed trait Association {
    def symbol: SymbolInfo
    def key: DIKey.BasicKey
    def tpe: TypeNative
    def name: String
    final def termName = u.TermName(name)
    def isByName: Boolean

    def asParameter: Association.Parameter
    def asParameterTpe: TypeNative
    def withKey(key: DIKey.BasicKey): Association
  }

  object Association {
    case class Parameter(symbol: SymbolInfo, key: DIKey.BasicKey) extends Association {
      override final def name: String = symbol.name
      override final def tpe: TypeNative = symbol.finalResultType
      override final def isByName: Boolean = symbol.isByName
      override final def withKey(key: DIKey.BasicKey): Association.Parameter = copy(key = key)
      override final def asParameter: Association.Parameter = this
      override final def asParameterTpe: TypeNative = tpe

      final def wasGeneric: Boolean = symbol.wasGeneric
    }

    // FIXME: non-existent wiring, at runtime there are only Parameters
    case class AbstractMethod(symbol: SymbolInfo, key: DIKey.BasicKey) extends Association {
      override final def name: String = symbol.name
      override final def tpe: TypeNative = symbol.finalResultType
      override final def isByName: Boolean = true
      override final def withKey(key: DIKey.BasicKey): Association.AbstractMethod = copy(key = key)
      override final def asParameter: Parameter = Parameter(symbol.withIsByName(true), key)
      override final def asParameterTpe: TypeNative = {
        // force by-name
        u.appliedType(u.definitions.ByNameParamClass, tpe)
      }
    }
  }

}
