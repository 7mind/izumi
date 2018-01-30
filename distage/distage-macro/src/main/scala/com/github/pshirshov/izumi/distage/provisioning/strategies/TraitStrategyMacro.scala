package com.github.pshirshov.izumi.distage.provisioning.strategies

import com.github.pshirshov.izumi.distage.model.functions.WrappedFunction

import scala.reflect.macros.blackbox

trait TraitStrategyMacro {
  def mkWrappedTraitConstructor[T]: WrappedFunction[T]

  def mkWrappedTraitConstructorMacro[T: blackbox.Context#WeakTypeTag](c: blackbox.Context): c.Expr[WrappedFunction[T]]
}
