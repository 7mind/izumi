package com.github.pshirshov.izumi.idealingua.runtime.rpc

trait IRTMethodSignature {
  type Input <: Product
  type Output <: Product

  def id: IRTMethodId
}
