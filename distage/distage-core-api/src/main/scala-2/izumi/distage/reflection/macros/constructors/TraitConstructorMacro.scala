package izumi.distage.reflection.macros.constructors

import izumi.distage.constructors.{DebugProperties, TraitConstructor}
import izumi.distage.model.providers.Functoid
import izumi.distage.reflection.macros.universe.{ReflectionProviderDefaultImpl, StaticDIUniverse}
import izumi.fundamentals.reflection.{ReflectionUtil, TrivialMacroLogger}

import scala.reflect.macros.blackbox

object TraitConstructorMacro {

  def mkTraitConstructor[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[TraitConstructor[T]] = {
    val macroUniverse = StaticDIUniverse(c)
    val impls = TraitConstructorMacros(c)(macroUniverse)
    import c.universe.*
    import impls.{c as _, u as _, *}

    val targetType = ReflectionUtil.norm(c.universe: c.universe.type)(weakTypeOf[T].dealias)
    requireConcreteTypeConstructor(c)("TraitConstructor", targetType)
    traitConstructorAssertion(targetType)

    val reflectionProvider = ReflectionProviderDefaultImpl(macroUniverse)
    val logger = TrivialMacroLogger.make[this.type](c, DebugProperties.`izumi.debug.macro.distage.constructors`.name)

    val provider: c.Expr[Functoid[T]] = mkTraitConstructorProvider(symbolToTrait(reflectionProvider)(targetType))

    val res = c.Expr[TraitConstructor[T]](q"{ new ${weakTypeOf[TraitConstructor[T]]}($provider) }")
    logger.log(s"Final syntax tree of trait $targetType:\n$res")

    res
  }

}
