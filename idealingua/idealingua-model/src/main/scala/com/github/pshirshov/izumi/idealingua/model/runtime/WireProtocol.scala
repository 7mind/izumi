package com.github.pshirshov.izumi.idealingua.model.runtime

import scala.util.Try

trait WireProtocol[OnWire] {
  def encode(generated: IDLGenerated): OnWire

  def decode(input: OnWire): IDLGenerated
}

trait AbstractTransport[Service <: IDLService] {
  def service: Service

  def inAcceptable(in: IDLGenerated): Boolean = service.companion.inputTag.runtimeClass.isAssignableFrom(in.getClass())
  def outAcceptable(out: IDLGenerated): Boolean = service.companion.outputTag.runtimeClass.isAssignableFrom(out.getClass())

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


trait MultiplexingWireTransport[OnWire] {
  def protocol: WireProtocol[OnWire]
  def services: Seq[AbstractTransport[_]]

  def process(request: OnWire): Try[OnWire] = {
    Try(protocol.decode(request))
      .map(r => (r, services.filter(_.inAcceptable(r))) )
      .map(rs => rs._2.map(_.process(rs._1)))
      .map(_.map(protocol.encode).head) // by design we have one service per input class
  }
}

class MultiplexingWireTransportDefaultImpl[OnWire]
(
  val protocol: WireProtocol[OnWire]
  , override val services: Seq[AbstractTransport[_]]
) extends MultiplexingWireTransport[OnWire] {
}