package com.github.pshirshov.izumi.idealingua.runtime.rpc

import io.circe.Json
import scalaz.zio.IO

import scala.language.higherKinds

class IRTClientMultiplexor[R[+_, +_] : IRTResultTransZio](clients: Set[IRTWrappedClient[R]]) {
  protected val ZIO: IRTResultTransZio[R] = implicitly[IRTResultTransZio[R]]

  val codecs: Map[IRTMethodId, IRTCirceMarshaller[R]] = clients.flatMap(_.allCodecs).toMap

  def encode(input: IRTMuxRequest): IO[Throwable, Json] = {
    codecs.get(input.method) match {
      case Some(marshaller) =>
        IO.syncThrowable(marshaller.encodeRequest(input.body))
      case None =>
        IO.terminate(new IRTMissingHandlerException(s"No codec for $input", input, None))
    }
  }

  def decode(input: Json, method: IRTMethodId): IO[Throwable, IRTMuxResponse] = {
    codecs.get(method) match {
      case Some(marshaller) =>
        ZIO.toZio(marshaller.decodeResponse(IRTJsonBody(method, input)))
          .map {
            body =>
              IRTMuxResponse(body, method)
          }


      case None =>
        IO.terminate(new IRTMissingHandlerException(s"No codec for $method, input=$input", input, None))
    }
  }
}
