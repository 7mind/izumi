package com.github.pshirshov.izumi.idealingua.model.runtime.transport

import com.github.pshirshov.izumi.idealingua.model.runtime.model.IDLService

class MultiplexingWireTransportDefaultImpl[InWire, OutWire]
(
  val protocol: WireProtocol[InWire, OutWire]
  , override val services: Seq[AbstractTransport[IDLService]]
) extends MultiplexingWireTransport[InWire, OutWire] {
}
