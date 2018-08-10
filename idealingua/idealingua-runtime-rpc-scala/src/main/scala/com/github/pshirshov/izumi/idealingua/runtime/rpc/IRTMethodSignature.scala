package com.github.pshirshov.izumi.idealingua.runtime.rpc

trait IRTMethodSignature extends IRTZioResult {
  type Input <: Product
  type Output <: Product

  def id: IRTMethodId
}
