package com.github.pshirshov.izumi.idealingua.model.runtime

import scala.util.Try

trait WireProtocol[OnWire] {
  def encode(generated: IDLGeneratedType): OnWire

  def decode(input: OnWire): IDLGeneratedType
}

trait AbstractTransport[Service <: IDLService] {
  def service: Service

  def inAcceptable(in: IDLGeneratedType): Boolean = service.inputTag.runtimeClass.isAssignableFrom(in.getClass)

  def outAcceptable(out: IDLGeneratedType): Boolean = service.outputTag.runtimeClass.isAssignableFrom(out.getClass)

  def process(request: Service#InputType): Service#OutputType

  def processUnsafe(request: IDLGenerated): IDLOutput = {
    process(request.asInstanceOf[Service#InputType])
  }
}

trait WireTransport[OnWire, Service <: IDLService] {
  def protocol: WireProtocol[OnWire]

  def abstractTransport: AbstractTransport[Service]

  def process(request: OnWire): Try[OnWire] = {
    Try(protocol.decode(request))
      .map(abstractTransport.processUnsafe)
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

  def services: Seq[AbstractTransport[IDLService]]

  def process(request: OnWire): Try[OnWire] = {
    Try(protocol.decode(request))
      .map(r => (r, services.filter(_.inAcceptable(r)).head)) // by design we have one service per input class
      .map(rs => rs._2.processUnsafe(rs._1))
      .map(protocol.encode)
  }
}

class MultiplexingWireTransportDefaultImpl[OnWire]
(
  val protocol: WireProtocol[OnWire]
  , override val services: Seq[AbstractTransport[IDLService]]
) extends MultiplexingWireTransport[OnWire] {
}
