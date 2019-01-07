package com.github.pshirshov.izumi.idealingua.runtime.rpc

trait IRTWrappedService[R[_, _], C] {
  def serviceId: IRTServiceId

  def allMethods: Map[IRTMethodId, IRTMethodWrapper[R, C]]
}
