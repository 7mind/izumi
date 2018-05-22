package com.github.pshirshov.izumi.distage.provisioning.strategies

import com.github.pshirshov.izumi.distage.model.definition.reflection.DIUniverseMacros
import com.github.pshirshov.izumi.distage.model.functions.WrappedFunction.DIKeyWrappedFunction
import com.github.pshirshov.izumi.distage.model.reflection.universe.{RuntimeDIUniverse, StaticDIUniverse}
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

    import tools.liftableRuntimeUniverse
    import tools.liftableAnnotation

    val targetType = weakTypeOf[T]

    val UnaryWiring.AbstractSymbol(_, wireables) = reflectionProvider.symbolToWiring(SafeType(targetType))

    val (wireArgs, w) = wireables.map {
      case AbstractMethod(_, methodSymbol, key) =>
        val tpe = key.symbol.tpe
        val methodName = methodSymbol.asMethod.name.toTermName
        val argName = c.freshName(methodName)

        val anns = tools.annotationsFromDIKey(key)

        (q"$anns val $argName: $tpe", (q"override val $methodName: $tpe = $argName", anns))
    }.unzip

    val (wireMethods, mods) = w.unzip

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

    val anns = mods.flatMap(_.annotations).map(Annotation(_)).toList

    val res = c.Expr[TraitConstructor[T]] {
      q"""
          {
          $constructorDef

          new ${weakTypeOf[TraitConstructor[T]]}($dikeyWrappedFunction.apply[$targetType](constructor _), $anns)
          }
       """
    }
    logger.log(s"Final syntax tree of trait $targetType:\n$res")

    res
  }
}
