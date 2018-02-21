package com.github.pshirshov.izumi.idealingua.model.runtime.transport

import com.github.pshirshov.izumi.idealingua.model.runtime.model.IDLService

class MultiplexingWireTransportDefaultImpl[OnWire]
(
  val protocol: WireProtocol[OnWire]
  , override val services: Seq[AbstractTransport[IDLService]]
) extends MultiplexingWireTransport[OnWire] {
}
