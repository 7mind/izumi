package com.github.pshirshov.izumi.distage.provisioning.strategies

import com.github.pshirshov.izumi.distage.model.reflection.universe.StaticDIUniverse
import com.github.pshirshov.izumi.distage.provisioning.{AnyConstructor, ConcreteConstructor}
import com.github.pshirshov.izumi.distage.reflection.SymbolIntrospectorDefaultImpl

import scala.reflect.macros.blackbox

object AnyConstructorMacro {
  def mkAnyConstructor[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[AnyConstructor[T]] = {
    import c.universe._

    val macroUniverse = StaticDIUniverse(c)
    val symbolIntrospector = SymbolIntrospectorDefaultImpl.Static(macroUniverse)

    val safe = macroUniverse.SafeType(weakTypeOf[T])

    if (symbolIntrospector.isConcrete(safe)) {
      c.Expr[AnyConstructor[T]] {
        q"""new ${weakTypeOf[ConcreteConstructor[T]]}($safe)"""
      }
    } else {
      AbstractConstructorMacro.mkAbstractConstructor[T](c)
    }
  }
}
