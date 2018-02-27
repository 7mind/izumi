package com.github.pshirshov.izumi.idealingua.model.runtime.transport

import com.github.pshirshov.izumi.idealingua.model.runtime.model.IDLService

class WireTransportDefaultImpl[InWire, OutWire, Service <: IDLService]
(
  val protocol: WireProtocol[InWire, Service#InputType, Service#Result[Service#OutputType], OutWire]
  , val abstractTransport: AbstractTransport[Service]
) extends WireTransport[InWire, OutWire, Service] {

}
