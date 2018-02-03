package com.github.pshirshov.izumi.idealingua.model.finaldef


trait DefMethod

object DefMethod {
  case class RPCMethod(name: String, signature: Signature) extends DefMethod
}