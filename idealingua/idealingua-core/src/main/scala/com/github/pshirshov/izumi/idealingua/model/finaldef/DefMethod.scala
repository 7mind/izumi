package com.github.pshirshov.izumi.idealingua.model.finaldef

import com.github.pshirshov.izumi.idealingua.model.common.TypeId

trait DefMethod

object DefMethod {
  case class RPCMethod(name: String, input: TypeId, output: TypeId) extends DefMethod
}