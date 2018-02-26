package com.github.pshirshov.izumi.idealingua.model.runtime.transport

import com.github.pshirshov.izumi.idealingua.model.runtime.model.IDLService

import scala.util.Try

trait WireTransport[OnWire, Service <: IDLService] {
  def protocol: WireProtocol[OnWire]

  def abstractTransport: AbstractTransport[Service]

  def process(request: OnWire): Try[OnWire] = {
    Try(protocol.decode(request))
      .map(abstractTransport.processUnsafe)
      .map(protocol.encode)
  }
}
