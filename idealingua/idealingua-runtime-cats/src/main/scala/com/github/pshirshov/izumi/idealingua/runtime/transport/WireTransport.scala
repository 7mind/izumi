package com.github.pshirshov.izumi.idealingua.runtime.transport

import com.github.pshirshov.izumi.idealingua.runtime.model.IDLService

import scala.language.higherKinds
import scala.util.Try

trait WireTransport[R[+_], InWire, OutWire, Service <: IDLService[R]] {
  def protocol: WireProtocol[InWire, Service#InputType, R[Service#OutputType], OutWire]

  def abstractTransport: AbstractServerDispatcher[R, Service]

  def process(request: InWire): Try[OutWire] = {
    Try(protocol.decode(request))
      .map(abstractTransport.dispatch)
      .map(protocol.encode)
  }
}
