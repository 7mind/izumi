package izumi.distage.constructors.macros

import izumi.distage.constructors.{DebugProperties, TraitConstructor}
import izumi.distage.model.providers.ProviderMagnet
import izumi.distage.model.reflection.universe.StaticDIUniverse
import izumi.distage.reflection.ReflectionProviderDefaultImpl
import izumi.fundamentals.reflection.{ReflectionUtil, TrivialMacroLogger}

import scala.reflect.macros.blackbox

object TraitConstructorMacro {

  def mkTraitConstructor[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[TraitConstructor[T]] = {
    val macroUniverse = StaticDIUniverse(c)
    val impls = TraitConstructorMacros(c)(macroUniverse)
    import c.universe._
    import impls.{c => _, u => _, _}

    val targetType = ReflectionUtil.norm(c.universe: c.universe.type)(weakTypeOf[T].dealias)
    requireConcreteTypeConstructor(c)("TraitConstructor", targetType)
    traitConstructorAssertion(targetType)

    val reflectionProvider = ReflectionProviderDefaultImpl(macroUniverse)
    val logger = TrivialMacroLogger.make[this.type](c, DebugProperties.`izumi.debug.macro.distage.constructors`)

    val provider: c.Expr[ProviderMagnet[T]] = mkTraitConstructorProvider(symbolToTrait(reflectionProvider)(targetType))

    val res = c.Expr[TraitConstructor[T]](q"{ new ${weakTypeOf[TraitConstructor[T]]}($provider) }")
    logger.log(s"Final syntax tree of trait $targetType:\n$res")

    res
  }

}
