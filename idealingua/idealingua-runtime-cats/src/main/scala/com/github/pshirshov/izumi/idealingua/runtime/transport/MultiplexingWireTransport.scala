package com.github.pshirshov.izumi.idealingua.runtime.transport

import com.github.pshirshov.izumi.idealingua.runtime.model.{IDLInput, IDLService}

import scala.language.higherKinds
import scala.util.Try

trait MultiplexingWireTransport[R[+_], InWire, Response, OutWire] {
  def protocol: WireProtocol[InWire, IDLInput, Response, OutWire]

  def services: Seq[UnsafeServerDispatcher[R, IDLService[R]]]

  def process(request: InWire): Try[OutWire] = {
    Try(protocol.decode(request))
      .map(r => (r, services.filter(_.inAcceptable(r)).head)) // by design we have one service per input class
      .map(rs => rs._2.dispatchUnsafe(rs._1).asInstanceOf[Response])
      .map(protocol.encode)
  }
}
