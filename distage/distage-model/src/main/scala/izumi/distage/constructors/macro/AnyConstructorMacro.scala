package izumi.distage.constructors.`macro`

import izumi.distage.constructors.AnyConstructor
import izumi.distage.model.reflection.universe.StaticDIUniverse
import izumi.distage.reflection.ReflectionProviderDefaultImpl
import izumi.fundamentals.reflection.ReflectionUtil

import scala.reflect.macros.blackbox

object AnyConstructorMacro {
  def mkAnyConstructor[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[AnyConstructor[T]] =
    mkAnyConstructorImpl[T](c, generateUnsafeWeakSafeTypes = false)
  def mkAnyConstructorUnsafeWeakSafeTypes[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[AnyConstructor[T]] =
    mkAnyConstructorImpl[T](c, generateUnsafeWeakSafeTypes = true)

  def mkAnyConstructorImpl[T: c.WeakTypeTag](c: blackbox.Context, generateUnsafeWeakSafeTypes: Boolean): c.Expr[AnyConstructor[T]] = {
    import c.universe._

    val macroUniverse = StaticDIUniverse(c)
    val reflectionProvider = ReflectionProviderDefaultImpl(macroUniverse)

    val tpe = ReflectionUtil.norm(c.universe: c.universe.type)(weakTypeOf[T])

    try {
      if (reflectionProvider.isConcrete(tpe)) {
        ConcreteConstructorMacro.mkConcreteConstructorImpl[T](c, generateUnsafeWeakSafeTypes)
      } else if (reflectionProvider.isFactory(tpe)) {
        FactoryConstructorMacro.mkFactoryConstructor[T](c)
      } else if (reflectionProvider.isWireableAbstract(tpe)) {
        TraitConstructorMacro.mkTraitConstructorImpl[T](c, generateUnsafeWeakSafeTypes)
      } else {
        c.abort(
          c.enclosingPosition
          ,
          s"""
             |The impossible happened! Cannot generate implementation for class $tpe!
             |Because it's neither a concrete class, nor a factory, nor a trait!
         """.stripMargin
        )
      }
    } catch {
      case _: Throwable =>
        c.Expr[AnyConstructor[T]](q"""{
          new _root_.izumi.distage.constructors.ConcreteConstructor[$tpe](
            _root_.izumi.distage.model.providers.ProviderMagnet[$tpe](() =>
              throw new _root_.izumi.distage.model.exceptions.UnsupportedDefinitionException("AnyConstructor failure: No constructor could be generated for " + ${tpe.toString}))
          )
        }""")
    }
  }
}
