package com.github.pshirshov.izumi.idealingua.model.runtime.transport

import com.github.pshirshov.izumi.idealingua.model.runtime.model.IDLService

class WireTransportDefaultImpl[OnWire, Service <: IDLService]
(
  val protocol: WireProtocol[OnWire]
  , val abstractTransport: AbstractTransport[Service]
) extends WireTransport[OnWire, Service] {

}
