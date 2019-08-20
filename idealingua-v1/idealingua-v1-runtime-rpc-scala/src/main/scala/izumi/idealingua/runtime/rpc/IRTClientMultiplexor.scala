package izumi.idealingua.runtime.rpc

import izumi.functional.bio.BIO
import izumi.functional.bio.BIO._
import io.circe.Json

class IRTClientMultiplexor[F[+ _, + _] : BIO](clients: Set[IRTWrappedClient]) {
  protected val BIO: BIO[F] = implicitly

  val codecs: Map[IRTMethodId, IRTCirceMarshaller] = clients.flatMap(_.allCodecs).toMap

  def encode(input: IRTMuxRequest): F[Throwable, Json] = {
    codecs.get(input.method) match {
      case Some(marshaller) =>
        BIO.syncThrowable(marshaller.encodeRequest(input.body))
      case None =>
        BIO.fail(new IRTMissingHandlerException(s"No codec for $input", input, None))
    }
  }

  def decode(input: Json, method: IRTMethodId): F[Throwable, IRTMuxResponse] = {
    codecs.get(method) match {
      case Some(marshaller) =>
        for {
          decoder <- BIO.syncThrowable(marshaller.decodeResponse[F].apply(IRTJsonBody(method, input)))
          body <- decoder
        } yield {
          IRTMuxResponse(body, method)
        }

      case None =>
        BIO.fail(new IRTMissingHandlerException(s"No codec for $method, input=$input", input, None))
    }
  }
}
