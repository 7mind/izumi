package com.github.pshirshov.izumi.idealingua.model.runtime.transport

import com.github.pshirshov.izumi.idealingua.model.runtime.model.IDLService

import scala.language.higherKinds
import scala.util.Try

trait WireTransport[R[_], InWire, OutWire, Service <: IDLService[R]] {
  def protocol: WireProtocol[InWire, Service#InputType, R[Service#OutputType], OutWire]

  def abstractTransport: AbstractTransport[R, Service]

  def process(request: InWire): Try[OutWire] = {
    Try(protocol.decode(request))
      .map(abstractTransport.process)
      .map(protocol.encode)
  }
}
