package com.github.pshirshov.izumi.idealingua.model.runtime.transport

import com.github.pshirshov.izumi.idealingua.model.runtime.model.IDLService

import scala.util.Try

trait WireTransport[InWire, OutWire, Service <: IDLService] {
  def protocol: WireProtocol[InWire, Service#InputType, Service#Result[Service#OutputType], OutWire]

  def abstractTransport: AbstractTransport[Service]

  def process(request: InWire): Try[OutWire] = {
    Try(protocol.decode(request))
      .map(abstractTransport.process)
      .map(protocol.encode)
  }
}
