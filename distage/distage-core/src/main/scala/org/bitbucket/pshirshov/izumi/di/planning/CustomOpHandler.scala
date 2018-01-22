package org.bitbucket.pshirshov.izumi.di.planning

import org.bitbucket.pshirshov.izumi.di.TypeFull
import org.bitbucket.pshirshov.izumi.di.definition.ImplDef
import org.bitbucket.pshirshov.izumi.di.model.exceptions.UnsupportedDefinitionException
import org.bitbucket.pshirshov.izumi.di.model.plan.Wiring

trait CustomOpHandler {
  def getDeps(op: ImplDef.CustomImpl): Wiring
  def getSymbol(op: ImplDef.CustomImpl): TypeFull
}

object CustomOpHandler {
  object NullCustomOpHander extends CustomOpHandler {
    override def getDeps(op: ImplDef.CustomImpl): Wiring = {
      throw new UnsupportedDefinitionException(s"Definition is not supported: $op", op)
    }

    override def getSymbol(op: ImplDef.CustomImpl): TypeFull = {
      throw new UnsupportedDefinitionException(s"Definition is not supported: $op", op)
    }
  }
}