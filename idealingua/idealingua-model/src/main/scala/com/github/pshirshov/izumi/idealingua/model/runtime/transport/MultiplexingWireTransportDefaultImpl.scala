package com.github.pshirshov.izumi.idealingua.model.runtime.transport

import com.github.pshirshov.izumi.idealingua.model.runtime.model.{IDLInput, IDLService}

import scala.language.higherKinds

class MultiplexingWireTransportDefaultImpl[R[_], InWire, Response, OutWire]
(
  val protocol: WireProtocol[InWire, IDLInput, Response, OutWire]
  , override val services: Seq[AbstractTransport[R, IDLService[R]]]
) extends MultiplexingWireTransport[R, InWire, Response, OutWire] {
}
