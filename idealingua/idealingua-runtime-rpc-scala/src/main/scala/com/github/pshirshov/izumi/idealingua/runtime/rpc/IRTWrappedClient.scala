package com.github.pshirshov.izumi.idealingua.runtime.rpc

trait IRTWrappedClient {
  def allCodecs: Map[IRTMethodId, IRTMarshaller]

}
