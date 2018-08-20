package com.github.pshirshov.izumi.idealingua.runtime.rpc

import io.circe.Json

import scala.language.higherKinds
import IRTResult._

class IRTClientMultiplexor[R[+ _, + _] : IRTResult](clients: Set[IRTWrappedClient[R]]) {
  protected val BIO: IRTResult[R] = implicitly

  val codecs: Map[IRTMethodId, IRTCirceMarshaller] = clients.flatMap(_.allCodecs).toMap

  def encode(input: IRTMuxRequest): R[Throwable, Json] = {
    codecs.get(input.method) match {
      case Some(marshaller) =>
        BIO.syncThrowable(marshaller.encodeRequest(input.body))
      case None =>
        BIO.terminate(new IRTMissingHandlerException(s"No codec for $input", input, None))
    }
  }

  def decode(input: Json, method: IRTMethodId): R[Throwable, IRTMuxResponse] = {
    codecs.get(method) match {
      case Some(marshaller) =>
        marshaller.decodeResponse[R].apply(IRTJsonBody(method, input))
          .map {
            body =>
              IRTMuxResponse(body, method)
          }


      case None =>
        BIO.terminate(new IRTMissingHandlerException(s"No codec for $method, input=$input", input, None))
    }
  }
}
