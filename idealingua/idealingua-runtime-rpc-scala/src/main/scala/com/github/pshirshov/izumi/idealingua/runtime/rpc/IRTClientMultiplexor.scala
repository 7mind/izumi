package com.github.pshirshov.izumi.idealingua.runtime.rpc

import io.circe.parser.parse
import scalaz.zio.IO

import scala.language.higherKinds

class IRTClientMultiplexor[R[_, _]](clients: Set[IRTWrappedClient[R]])
  extends IRTResultZio {
  val codecs: Map[IRTMethodId, IRTMarshaller[R]] = clients.flatMap(_.allCodecs).toMap

  def encode(input: IRTMuxRequest[Product]): IO[Throwable, String] = {
    codecs.get(input.method) match {
      case Some(marshaller) =>
        IO.syncThrowable(marshaller.encodeRequest(input.body).noSpaces)
      case None =>
        IO.terminate(new IRTMissingHandlerException(s"No codec for $input", input, None))
    }
  }

  def decode(input: String, method: IRTMethodId): IO[Throwable, IRTMuxResponse[Product]] = {
    codecs.get(method) match {
      case Some(marshaller) =>
        IO.syncThrowable(parse(input)).flatMap {
          case Right(parsed) =>
            marshaller.toZio(marshaller.decodeResponse(IRTJsonBody(method, parsed)))
              .map {
                body =>
                  IRTMuxResponse(body, method)
              }

          case Left(t) =>
            IO.terminate(new IRTUnparseableDataException(s"Unparseable $method, input=$input", Some(t)))
        }

      case None =>
        IO.terminate(new IRTMissingHandlerException(s"No codec for $method, input=$input", input, None))
    }
  }
}
