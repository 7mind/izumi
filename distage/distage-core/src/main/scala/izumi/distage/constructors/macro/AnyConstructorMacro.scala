package izumi.distage.constructors.`macro`

import izumi.distage.constructors.AnyConstructor
import izumi.distage.model.reflection.universe.StaticDIUniverse
import izumi.distage.reflection.SymbolIntrospectorDefaultImpl

import scala.reflect.macros.blackbox

object AnyConstructorMacro {
  def mkAnyConstructor[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[AnyConstructor[T]] = mkAnyConstructorImpl[T](c, generateUnsafeWeakSafeTypes = false)

  def mkAnyConstructorUnsafeWeakSafeTypes[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[AnyConstructor[T]] = mkAnyConstructorImpl[T](c, generateUnsafeWeakSafeTypes = true)

  def mkAnyConstructorImpl[T: c.WeakTypeTag](c: blackbox.Context, generateUnsafeWeakSafeTypes: Boolean): c.Expr[AnyConstructor[T]] = {
    import c.universe._

    val macroUniverse = StaticDIUniverse(c)
    val symbolIntrospector = SymbolIntrospectorDefaultImpl.Static(macroUniverse)

    import macroUniverse._

    val safe = SafeType(weakTypeOf[T])

    if (symbolIntrospector.isConcrete(safe)) {
      ConcreteConstructorMacro.mkConcreteConstructorImpl[T](c, generateUnsafeWeakSafeTypes)
    } else if (symbolIntrospector.isFactory(safe)) {
      FactoryConstructorMacro.mkFactoryConstructor[T](c)
    } else if (symbolIntrospector.isWireableAbstract(safe)) {
      TraitConstructorMacro.mkTraitConstructorImpl[T](c, generateUnsafeWeakSafeTypes)
    } else {
      c.abort(
        c.enclosingPosition
        , s"""
           |The impossible happened! Cannot generate implementation for class $safe!
           |Because it's neither a concrete class, nor a factory, nor a trait!
         """.stripMargin
      )
    }
  }
}
