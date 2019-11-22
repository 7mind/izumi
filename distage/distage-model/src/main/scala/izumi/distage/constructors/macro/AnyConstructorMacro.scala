package izumi.distage.constructors.`macro`

import izumi.distage.constructors.{AnyConstructor, ConcreteConstructor}
import izumi.distage.model.providers.ProviderMagnet
import izumi.distage.model.reflection.universe.{RuntimeDIUniverse, StaticDIUniverse}
import izumi.distage.reflection.SymbolIntrospectorDefaultImpl

import scala.reflect.macros.blackbox

object AnyConstructorMacro {
  def mkAnyConstructor[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[AnyConstructor[T]] =
    mkAnyConstructorImpl[T](c, generateUnsafeWeakSafeTypes = false)
  def mkAnyConstructorUnsafeWeakSafeTypes[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[AnyConstructor[T]] =
    mkAnyConstructorImpl[T](c, generateUnsafeWeakSafeTypes = true)

  def mkAnyConstructorImpl[T: c.WeakTypeTag](c: blackbox.Context, generateUnsafeWeakSafeTypes: Boolean): c.Expr[AnyConstructor[T]] = {
    import c.universe._

    val macroUniverse = StaticDIUniverse(c)
    val symbolIntrospector = SymbolIntrospectorDefaultImpl.Static(macroUniverse)

    import macroUniverse._

    val safe = SafeType(weakTypeOf[T])

    try {
      if (symbolIntrospector.isConcrete(safe)) {
        ConcreteConstructorMacro.mkConcreteConstructorImpl[T](c, generateUnsafeWeakSafeTypes)
      } else if (symbolIntrospector.isFactory(safe)) {
        FactoryConstructorMacro.mkFactoryConstructor[T](c)
      } else if (symbolIntrospector.isWireableAbstract(safe)) {
        TraitConstructorMacro.mkTraitConstructorImpl[T](c, generateUnsafeWeakSafeTypes)
      } else {
        c.abort(
          c.enclosingPosition
          ,
          s"""
             |The impossible happened! Cannot generate implementation for class $safe!
             |Because it's neither a concrete class, nor a factory, nor a trait!
         """.stripMargin
        )
      }
    } catch {
      case _: Throwable =>
        val T = weakTypeOf[T]
        c.Expr[AnyConstructor[T]](q"""{
          new _root_.izumi.distage.constructors.ConcreteConstructor[$T](
            _root_.izumi.distage.model.providers.ProviderMagnet.todoProvider(
              _root_.izumi.distage.model.reflection.universe.RuntimeDIUniverse.DIKey.get[$T]
            ).asInstanceOf[_root_.izumi.distage.model.providers.ProviderMagnet[$T]]
          )
        }""")
    }
  }
}
