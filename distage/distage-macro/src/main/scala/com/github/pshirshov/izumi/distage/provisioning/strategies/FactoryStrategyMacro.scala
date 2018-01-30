package com.github.pshirshov.izumi.distage.provisioning.strategies

import com.github.pshirshov.izumi.distage.model.functions.WrappedFunction

import scala.reflect.macros.blackbox

trait FactoryStrategyMacro {
  def mkWrappedFactoryConstructor[T]: WrappedFunction[T]

  def mkWrappedFactoryConstructorMacro[T: blackbox.Context#WeakTypeTag](c: blackbox.Context): c.Expr[WrappedFunction[T]]
}
