package com.github.pshirshov.izumi.idealingua.runtime.rpc

trait IRTMethodSignature extends IRTResultZio {
  type Input <: Product
  type Output <: Product

  def id: IRTMethodId
}
