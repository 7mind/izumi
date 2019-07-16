package com.github.pshirshov.izumi.idealingua.runtime.rpc

import com.github.pshirshov.izumi.functional.bio.{BIO, F}
import io.circe.Json

class IRTClientMultiplexor[R[+ _, + _]: BIO](clients: Set[IRTWrappedClient]) {
  val codecs: Map[IRTMethodId, IRTCirceMarshaller] = clients.flatMap(_.allCodecs).toMap

  def encode(input: IRTMuxRequest): R[Throwable, Json] = {
    codecs.get(input.method) match {
      case Some(marshaller) =>
        F.syncThrowable(marshaller.encodeRequest(input.body))
      case None =>
        F.fail(new IRTMissingHandlerException(s"No codec for $input", input, None))
    }
  }

  def decode(input: Json, method: IRTMethodId): R[Throwable, IRTMuxResponse] = {
    codecs.get(method) match {
      case Some(marshaller) =>
        for {
          decoder <- F.syncThrowable(marshaller.decodeResponse[R].apply(IRTJsonBody(method, input)))
          body <- decoder
        } yield {
          IRTMuxResponse(body, method)
        }

      case None =>
        F.fail(new IRTMissingHandlerException(s"No codec for $method, input=$input", input, None))
    }
  }
}
