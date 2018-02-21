package com.github.pshirshov.izumi.idealingua.model.runtime.transport

import com.github.pshirshov.izumi.idealingua.model.runtime.model.IDLService

import scala.util.Try

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
