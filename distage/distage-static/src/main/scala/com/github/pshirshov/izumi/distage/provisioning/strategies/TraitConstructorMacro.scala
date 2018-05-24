package com.github.pshirshov.izumi.distage.provisioning.strategies

import com.github.pshirshov.izumi.distage.model.definition.reflection.DIUniverseMacros
import com.github.pshirshov.izumi.distage.model.functions.WrappedFunction.DIKeyWrappedFunction
import com.github.pshirshov.izumi.distage.model.reflection.universe.StaticDIUniverse
import com.github.pshirshov.izumi.distage.provisioning.TraitConstructor
import com.github.pshirshov.izumi.distage.reflection.{DependencyKeyProviderDefaultImpl, ReflectionProviderDefaultImpl, SymbolIntrospectorDefaultImpl}
import com.github.pshirshov.izumi.fundamentals.reflection.MacroUtil

import scala.reflect.macros.blackbox

object TraitConstructorMacro {

  def mkTraitConstructor[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[TraitConstructor[T]] = {
    import c.universe._

    val macroUniverse = StaticDIUniverse(c)
    import macroUniverse._
    import macroUniverse.Wiring._
    import macroUniverse.Association._

    val symbolIntrospector = SymbolIntrospectorDefaultImpl.Static(macroUniverse)
    val keyProvider = DependencyKeyProviderDefaultImpl.Static(macroUniverse)(symbolIntrospector)
    val reflectionProvider = ReflectionProviderDefaultImpl.Static(macroUniverse)(keyProvider, symbolIntrospector)
    val tools = DIUniverseMacros(macroUniverse)
    val logger = MacroUtil.mkLogger[this.type](c)

    val targetType = weakTypeOf[T]

    val UnaryWiring.AbstractSymbol(_, wireables) = reflectionProvider.symbolToWiring(SafeType(targetType))

    val (wireArgs, wireMethods) = wireables.map {
      case AbstractMethod(_, methodSymbol, key) =>
        val tpe = key.tpe.tpe
        val methodName: TermName = TermName(methodSymbol.name)
        val argName: TermName = c.freshName(methodName)

        val mods = tools.modifiersForAnns(methodSymbol.annotations)

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

    val dikeyWrappedFunction = symbolOf[DIKeyWrappedFunction.type].asClass.module

    val res = c.Expr[TraitConstructor[T]] {
      q"""
          {
          $constructorDef

          new ${weakTypeOf[TraitConstructor[T]]}($dikeyWrappedFunction.apply[$targetType](constructor _))
          }
       """
    }
    logger.log(s"Final syntax tree of trait $targetType:\n$res")

    res
  }
}
