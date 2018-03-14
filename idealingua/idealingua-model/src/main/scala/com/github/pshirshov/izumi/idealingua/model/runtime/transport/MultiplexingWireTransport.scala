package com.github.pshirshov.izumi.idealingua.model.runtime.transport

import com.github.pshirshov.izumi.idealingua.model.runtime.model.{IDLInput, IDLService}

import scala.util.Try

trait MultiplexingWireTransport[InWire, Response, OutWire] {
  def protocol: WireProtocol[InWire, IDLInput, Response, OutWire]

  def services: Seq[AbstractTransport[IDLService]]

  def process(request: InWire): Try[OutWire] = {
    Try(protocol.decode(request))
      .map(r => (r, services.filter(_.inAcceptable(r)).head)) // by design we have one service per input class
      .map(rs => rs._2.processUnsafe(rs._1).asInstanceOf[Response])
      .map(protocol.encode)
  }
}
