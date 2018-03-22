package com.github.pshirshov.izumi.idealingua.runtime.transport

import com.github.pshirshov.izumi.idealingua.runtime.model.IDLService

import scala.language.higherKinds

class WireTransportDefaultImpl[R[_], InWire, OutWire, Service <: IDLService[R]]
(
  val protocol: WireProtocol[InWire, Service#InputType, Service#Result[Service#OutputType], OutWire]
  , val abstractTransport: AbstractServerDispatcher[R, Service]
) extends WireTransport[R, InWire, OutWire, Service] {

}
