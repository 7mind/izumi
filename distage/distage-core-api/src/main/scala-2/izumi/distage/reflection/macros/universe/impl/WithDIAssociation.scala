package izumi.distage.reflection.macros.universe.impl

import scala.reflect.macros.blackbox

private[distage] trait WithDIAssociation { this: DIUniverseBase with WithDISafeType with WithDIKey with WithDISymbolInfo =>

  sealed trait Association {
    def symbol: MacroSymbolInfo
    def key: MacroDIKey.BasicKey
    final def name: String = symbol.name

    /** methods are always by-name */
    def isByName: Boolean

    def asParameter: Association.Parameter

    /** never by-name for methods, - may be by-name for parameters */
    final def tpe: TypeNative = symbol.finalResultType

    final def nonBynameTpe: TypeNative = symbol.nonByNameFinalResultType

    /** always by-name for methods, - may be by-name for parameters */
    def asParameterTpe: TypeNative

    final def ctorArgumentExpr(c: blackbox.Context): (u.Tree, u.Tree) = {
      import u.*
      val freshArgName = u.TermName(c.freshName(name))
      (q"val $freshArgName: $asParameterTpe", Liftable.liftName(freshArgName))
    }
    final def traitMethodExpr(impl: u.Tree): u.Tree = {
      import u.*
      q"final lazy val ${TermName(name)}: $nonBynameTpe = $impl"
    }
  }

  object Association {
    case class Parameter(symbol: MacroSymbolInfo, key: MacroDIKey.BasicKey) extends Association {
      override final def isByName: Boolean = symbol.isByName
      override final def asParameter: Association.Parameter = this
      override final def asParameterTpe: TypeNative = tpe
    }

    // tpe is never by-name for `AbstractMethod`
    case class AbstractMethod(symbol: MacroSymbolInfo, key: MacroDIKey.BasicKey) extends Association {
      override final def isByName: Boolean = true
      override final def asParameter: Parameter = Parameter(symbol.withIsByName(true).withTpe(asParameterTpe), key)
      override final def asParameterTpe: TypeNative = u.appliedType(u.definitions.ByNameParamClass, tpe) // force by-name
    }
  }

}
