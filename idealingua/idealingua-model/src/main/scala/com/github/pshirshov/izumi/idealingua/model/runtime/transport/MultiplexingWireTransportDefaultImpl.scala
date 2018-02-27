package com.github.pshirshov.izumi.idealingua.model.runtime.transport

import com.github.pshirshov.izumi.idealingua.model.runtime.model.{IDLInput, IDLService}

class MultiplexingWireTransportDefaultImpl[InWire, Response, OutWire]
(
  val protocol: WireProtocol[InWire, IDLInput, Response, OutWire]
  , override val services: Seq[AbstractTransport[IDLService]]
) extends MultiplexingWireTransport[InWire, Response, OutWire] {
}
