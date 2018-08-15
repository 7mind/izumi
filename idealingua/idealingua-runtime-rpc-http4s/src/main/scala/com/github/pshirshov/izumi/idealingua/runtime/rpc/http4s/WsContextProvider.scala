package com.github.pshirshov.izumi.idealingua.runtime.rpc.http4s

import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks
import com.github.pshirshov.izumi.idealingua.runtime.rpc.RpcRequest

trait WsContextProvider[Ctx] {
  def toContext(initial: Ctx, packet: RpcRequest): Ctx
}

object WsContextProvider {
  def id[Ctx]: WsContextProvider[Ctx] = (initial: Ctx, packet: RpcRequest) => {
    Quirks.discard(packet)
    initial
  }
}
