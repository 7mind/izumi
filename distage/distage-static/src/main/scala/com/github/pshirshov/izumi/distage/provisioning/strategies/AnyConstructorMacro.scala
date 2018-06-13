package com.github.pshirshov.izumi.distage.provisioning.strategies

import com.github.pshirshov.izumi.distage.model.definition.reflection.DIUniverseMacros
import com.github.pshirshov.izumi.distage.model.reflection.universe.StaticDIUniverse
import com.github.pshirshov.izumi.distage.provisioning.AnyConstructor
import com.github.pshirshov.izumi.distage.reflection.SymbolIntrospectorDefaultImpl

import scala.reflect.macros.blackbox

object AnyConstructorMacro {
  def mkAnyConstructor[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[AnyConstructor[T]] = {
    import c.universe._

    val macroUniverse = StaticDIUniverse(c)
    val symbolIntrospector = SymbolIntrospectorDefaultImpl.Static(macroUniverse)
    val tools = DIUniverseMacros(macroUniverse)

    import macroUniverse._

    val safe = SafeType(weakTypeOf[T])

    if (symbolIntrospector.isConcrete(safe)) {
      ConcreteConstructorMacro.mkConcreteConstructor[T](c)
    } else if (symbolIntrospector.isFactory(safe)) {
      FactoryConstructorMacro.mkFactoryConstructor[T](c)
    } else if (symbolIntrospector.isWireableAbstract(safe)) {
      TraitConstructorMacro.mkTraitConstructor[T](c)
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
