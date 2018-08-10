package com.github.pshirshov.izumi.idealingua.runtime.rpc

trait IRTWrappedService[C] {
  def serviceId: IRTServiceId

  def allMethods: Map[IRTMethodId, IRTMethodWrapper[C]]

  def allCodecs: Map[IRTMethodId, IRTMarshaller]
}
