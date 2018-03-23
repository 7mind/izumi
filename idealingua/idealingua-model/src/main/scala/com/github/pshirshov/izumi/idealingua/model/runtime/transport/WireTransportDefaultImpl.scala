package com.github.pshirshov.izumi.idealingua.model.runtime.transport

import com.github.pshirshov.izumi.idealingua.model.runtime.model.IDLService

import scala.language.higherKinds

class WireTransportDefaultImpl[R[_], InWire, OutWire, Service <: IDLService[R]]
(
  val protocol: WireProtocol[InWire, Service#InputType, Service#Result[Service#OutputType], OutWire]
  , val abstractTransport: AbstractTransport[R, Service]
) extends WireTransport[R, InWire, OutWire, Service] {

}
