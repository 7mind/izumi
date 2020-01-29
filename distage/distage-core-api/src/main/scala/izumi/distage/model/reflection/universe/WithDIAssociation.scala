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
    /** never by-name for methods, may be by-name for parameters */
    final def tpe: TypeNative = symbol.finalResultType
    final def nonBynameTpe: TypeNative = symbol.nonByNameFinalResultType
    final def name: String = symbol.name
    final def termName: u.TermName = u.TermName(name)
    // methods are always by-name
    def isByName: Boolean

    def asParameter: Association.Parameter
    def asParameterTpe: TypeNative
  }

  object Association {
    case class Parameter(symbol: SymbolInfo, key: DIKey.BasicKey) extends Association {
      override final def isByName: Boolean = symbol.isByName
      override final def asParameter: Association.Parameter = this
      override final def asParameterTpe: TypeNative = tpe
    }

    // FIXME: non-existent wiring, at runtime there are only Parameters
    case class AbstractMethod(symbol: SymbolInfo, key: DIKey.BasicKey) extends Association {
      override final def isByName: Boolean = true
      override final def asParameter: Parameter = Parameter(symbol.withIsByName(true).withTpe(asParameterTpe), key)
      override final def asParameterTpe: TypeNative = {
        // force by-name
        u.appliedType(u.definitions.ByNameParamClass, tpe)
      }
    }
  }

}
