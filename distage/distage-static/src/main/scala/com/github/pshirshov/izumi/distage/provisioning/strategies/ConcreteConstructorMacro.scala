package com.github.pshirshov.izumi.distage.provisioning.strategies

import com.github.pshirshov.izumi.distage.model.providers.ProviderMagnet
import com.github.pshirshov.izumi.distage.model.reflection.universe.StaticDIUniverse
import com.github.pshirshov.izumi.distage.provisioning.ConcreteConstructor
import com.github.pshirshov.izumi.distage.reflection.{DependencyKeyProviderDefaultImpl, ReflectionProviderDefaultImpl, SymbolIntrospectorDefaultImpl}
import com.github.pshirshov.izumi.fundamentals.reflection.{AnnotationTools, MacroUtil}

import scala.reflect.macros.blackbox

object ConcreteConstructorMacro {

  def mkConcreteConstructor[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[ConcreteConstructor[T]] = mkConcreteConstructorImpl[T](c, generateUnsafeWeakSafeTypes = false)

  def mkConcreteConstructorImpl[T: c.WeakTypeTag](c: blackbox.Context, generateUnsafeWeakSafeTypes: Boolean): c.Expr[ConcreteConstructor[T]] = {
    import c.universe._

    val macroUniverse = StaticDIUniverse(c)
    import macroUniverse._

    val symbolIntrospector = SymbolIntrospectorDefaultImpl.Static(macroUniverse)
    val keyProvider = DependencyKeyProviderDefaultImpl.Static(macroUniverse)(symbolIntrospector)
    val reflectionProvider = ReflectionProviderDefaultImpl.Static(macroUniverse)(keyProvider, symbolIntrospector)
    val logger = MacroUtil.mkLogger[this.type](c)

    val targetType = weakTypeOf[T]

    if (!symbolIntrospector.isConcrete(SafeType(targetType))) {
      c.abort(c.enclosingPosition,
        s"""Tried to derive constructor function for class $targetType, but the class is an
           |abstract class or a trait! Only concrete classes (`class` keyword) are supported""".stripMargin)
    }

    val paramLists = reflectionProvider.constructorParameterLists(SafeType(targetType))

    val fnArgsNamesLists = paramLists.map(_.map {
      p =>
        val name = c.freshName(TermName(p.name))

        val mods = AnnotationTools.mkModifiers(u)(p.context.symbol.annotations)

        q"$mods val $name: ${p.wireWith.tpe.tpe}" -> name
    })

    val args = fnArgsNamesLists.flatten.unzip._1
    val argNamesLists = fnArgsNamesLists.map(_.map(_._2))

    val fn = q"(..$args) => new $targetType(...$argNamesLists)"

    val providerMagnet = symbolOf[ProviderMagnet.type].asClass.module

    val provided =
      if (generateUnsafeWeakSafeTypes)
        q"{ $providerMagnet.generateUnsafeWeakSafeTypes[$targetType]($fn) }"
      else
        q"{ $providerMagnet.apply[$targetType]($fn) }"

    val res = c.Expr[ConcreteConstructor[T]] {
      q"{ new ${weakTypeOf[ConcreteConstructor[T]]}($provided) }"
    }
    logger.log(s"Final syntax tree of concrete constructor for $targetType:\n$res")

    res
  }
}

