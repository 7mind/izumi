package com.github.pshirshov.izumi.distage.model.planning

import com.github.pshirshov.izumi.distage.model.definition.ImplDef
import com.github.pshirshov.izumi.distage.model.exceptions.UnsupportedDefinitionException
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse

trait CustomOpHandler {
  def getDeps(op: ImplDef.CustomImpl): RuntimeDIUniverse.Wiring
  def getSymbol(op: ImplDef.CustomImpl): RuntimeDIUniverse.TypeFull
}

object CustomOpHandler {
  object NullCustomOpHander extends CustomOpHandler {
    override def getDeps(op: ImplDef.CustomImpl): RuntimeDIUniverse.Wiring = {
      throw new UnsupportedDefinitionException(s"Definition is not supported: $op", op)
    }

    override def getSymbol(op: ImplDef.CustomImpl): RuntimeDIUniverse.TypeFull = {
      throw new UnsupportedDefinitionException(s"Definition is not supported: $op", op)
    }
  }
}
