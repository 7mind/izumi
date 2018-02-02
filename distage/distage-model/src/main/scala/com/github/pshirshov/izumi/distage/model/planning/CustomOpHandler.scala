package com.github.pshirshov.izumi.distage.model.planning

import com.github.pshirshov.izumi.distage.model.definition.ImplDef
import com.github.pshirshov.izumi.distage.model.exceptions.UnsupportedDefinitionException
import com.github.pshirshov.izumi.distage.model.plan.Wiring
import com.github.pshirshov.izumi.fundamentals.reflection._

trait CustomOpHandler {
  def getDeps(op: ImplDef.CustomImpl): Wiring
  def getSymbol(op: ImplDef.CustomImpl): RuntimeUniverse.TypeFull
}

object CustomOpHandler {
  object NullCustomOpHander extends CustomOpHandler {
    override def getDeps(op: ImplDef.CustomImpl): Wiring = {
      throw new UnsupportedDefinitionException(s"Definition is not supported: $op", op)
    }

    override def getSymbol(op: ImplDef.CustomImpl): RuntimeUniverse.TypeFull = {
      throw new UnsupportedDefinitionException(s"Definition is not supported: $op", op)
    }
  }
}