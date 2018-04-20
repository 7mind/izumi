package com.github.pshirshov.izumi.distage.provisioning.strategies


import com.github.pshirshov.izumi.distage.model.functions.WrappedFunction.DIKeyWrappedFunction

import scala.reflect.macros.blackbox

trait FactoryStrategyMacro {
  def mkWrappedFactoryConstructor[T]: DIKeyWrappedFunction[T]

  def mkWrappedFactoryConstructorMacro[T: blackbox.Context#WeakTypeTag](c: blackbox.Context): c.Expr[DIKeyWrappedFunction[T]]
}
