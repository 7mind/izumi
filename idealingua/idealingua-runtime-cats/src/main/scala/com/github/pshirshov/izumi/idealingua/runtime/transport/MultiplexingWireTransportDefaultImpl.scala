package com.github.pshirshov.izumi.idealingua.runtime.transport

import com.github.pshirshov.izumi.idealingua.runtime.model.{IDLInput, IDLService}

import scala.language.higherKinds

class MultiplexingWireTransportDefaultImpl[R[_], InWire, Response, OutWire]
(
  val protocol: WireProtocol[InWire, IDLInput, Response, OutWire]
  , override val services: Seq[UnsafeServerDispatcher[R, IDLService[R]]]
) extends MultiplexingWireTransport[R, InWire, Response, OutWire] {
}
