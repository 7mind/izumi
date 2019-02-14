package com.github.pshirshov.izumi.idealingua.runtime.rpc

import cats.syntax.apply
import com.github.pshirshov.izumi.functional.bio.BIO
import com.github.pshirshov.izumi.functional.bio.BIO._
import io.circe.Json

class IRTClientMultiplexor[R[+ _, + _] : BIO](clients: Set[IRTWrappedClient]) {
  protected val BIO: BIO[R] = implicitly

  val codecs: Map[IRTMethodId, IRTCirceMarshaller] = clients.flatMap(_.allCodecs).toMap

  def encode(input: IRTMuxRequest): R[Throwable, Json] = {
    codecs.get(input.method) match {
      case Some(marshaller) =>
        BIO.syncThrowable(marshaller.encodeRequest(input.body))
      case None =>
        BIO.fail(new IRTMissingHandlerException(s"No codec for $input", input, None))
    }
  }

  def decode(input: Json, method: IRTMethodId): R[Throwable, IRTMuxResponse] = {
    codecs.get(method) match {
      case Some(marshaller) =>
        for {
          decoder <- BIO.syncThrowable(marshaller.decodeResponse[R].apply(IRTJsonBody(method, input)))
          body <- decoder
        } yield {
          IRTMuxResponse(body, method)
        }

      case None =>
        BIO.fail(new IRTMissingHandlerException(s"No codec for $method, input=$input", input, None))
    }
  }
}
