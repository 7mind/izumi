package com.github.pshirshov.izumi.distage.provisioning.strategies

import com.github.pshirshov.izumi.distage.model.providers.ProviderMagnet
import com.github.pshirshov.izumi.distage.model.reflection.macros.TrivialMacroLogger
import com.github.pshirshov.izumi.distage.model.reflection.universe.StaticDIUniverse
import com.github.pshirshov.izumi.distage.provisioning.TraitConstructor
import com.github.pshirshov.izumi.distage.reflection.{DependencyKeyProviderDefaultImpl, ReflectionProviderDefaultImpl, SymbolIntrospectorDefaultImpl}
import com.github.pshirshov.izumi.fundamentals.reflection.AnnotationTools

import scala.reflect.macros.blackbox

object TraitConstructorMacro {

  def mkTraitConstructor[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[TraitConstructor[T]] = mkTraitConstructorImpl[T](c, generateUnsafeWeakSafeTypes = false)

  def mkTraitConstructorImpl[T: c.WeakTypeTag](c: blackbox.Context, generateUnsafeWeakSafeTypes: Boolean): c.Expr[TraitConstructor[T]] = {
    import c.universe._

    val macroUniverse = StaticDIUniverse(c)
    import macroUniverse._
    import macroUniverse.Wiring._
    import macroUniverse.Association._

    val symbolIntrospector = SymbolIntrospectorDefaultImpl.Static(macroUniverse)
    val keyProvider = DependencyKeyProviderDefaultImpl.Static(macroUniverse)(symbolIntrospector)
    val reflectionProvider = ReflectionProviderDefaultImpl.Static(macroUniverse)(keyProvider, symbolIntrospector)
    val logger = TrivialMacroLogger[this.type](c)

    val targetType = weakTypeOf[T]

    val UnaryWiring.AbstractSymbol(_, wireables) = reflectionProvider.symbolToWiring(SafeType(targetType))

    val (wireArgs, wireMethods) = wireables.map {
      case AbstractMethod(ctx, name, _, key) =>
        val tpe = key.tpe.tpe
        val methodName: TermName = TermName(name)
        val argName: TermName = c.freshName(methodName)

        val mods = AnnotationTools.mkModifiers(u)(ctx.methodSymbol.annotations)

        q"$mods val $argName: $tpe" -> q"override val $methodName: $tpe = $argName"
    }.unzip

    val instantiate = if (wireMethods.isEmpty)
      q"new $targetType {}"
    else
      q"new $targetType { ..$wireMethods }"

    val constructorDef = q"""
      ${if (wireArgs.nonEmpty)
          q"def constructor(..$wireArgs): $targetType = ($instantiate).asInstanceOf[$targetType]"
        else
          q"def constructor: $targetType = ($instantiate).asInstanceOf[$targetType]"
      }
      """

    val providerMagnet = symbolOf[ProviderMagnet.type].asClass.module

    val provided =
      if (generateUnsafeWeakSafeTypes)
        q"{ $providerMagnet.generateUnsafeWeakSafeTypes[$targetType](constructor _) }"
      else
        q"{ $providerMagnet.apply[$targetType](constructor _) }"

    val res = c.Expr[TraitConstructor[T]] {
      q"""
          {
          $constructorDef

          new ${weakTypeOf[TraitConstructor[T]]}($provided)
          }
       """
    }
    logger.log(s"Final syntax tree of trait $targetType:\n$res")

    res
  }
}
