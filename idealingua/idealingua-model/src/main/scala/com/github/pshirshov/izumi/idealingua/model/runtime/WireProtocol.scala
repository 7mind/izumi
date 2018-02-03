package com.github.pshirshov.izumi.idealingua.model.runtime

import scala.util.Try

trait WireProtocol[OnWire] {
  def encode(generated: IDLGenerated): OnWire

  def decode(input: OnWire): IDLGenerated
}

trait AbstractTransport[Service <: IDLService] {
  def service: Service

  def process(request: IDLGenerated): IDLGenerated
}

trait WireTransport[OnWire, Service <: IDLService] {
  def protocol: WireProtocol[OnWire]

  def abstractTransport: AbstractTransport[Service]

  def process(request: OnWire): Try[OnWire] = {
    Try(protocol.decode(request))
      .map(abstractTransport.process)
      .map(protocol.encode)
  }
}

class WireTransportDefaultImpl[OnWire, Service <: IDLService]
(
  val protocol: WireProtocol[OnWire]
  , val abstractTransport: AbstractTransport[Service]
) extends WireTransport[OnWire, Service] {

}
