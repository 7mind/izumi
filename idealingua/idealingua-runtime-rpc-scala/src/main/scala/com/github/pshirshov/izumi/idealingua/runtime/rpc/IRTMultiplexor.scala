package com.github.pshirshov.izumi.idealingua.runtime.rpc

import io.circe.ParsingFailure
import io.circe.parser._
import scalaz.zio.IO

class IRTCodec(clients: Set[IRTWrappedClient]) extends IRTZioResult {
  val codecs: Map[IRTMethodId, IRTMarshaller] = clients.flatMap(_.allCodecs).toMap

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
            marshaller.decodeResponse(IRTRawCall(method, parsed)).map {
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

class IRTMultiplexor[C](list: Set[IRTWrappedService[C]]) extends IRTZioResult {
  val services: Map[IRTServiceId, IRTWrappedService[C]] = list.map(s => s.serviceId -> s).toMap


  def doInvoke(body: String, context: C, toInvoke: IRTMethodId): Either[ParsingFailure, Option[IO[Throwable, String]]] = {
    for {
      parsed <- _root_.io.circe.parser.parse(body)
    } yield {
      for {
        service <- services.get(toInvoke.service)
        method <- service.allMethods.get(toInvoke)
      } yield {
        for {
          decoded <- method.marshaller.decodeRequest(IRTRawCall(toInvoke, parsed))
          casted <- IO.syncThrowable(decoded.value.asInstanceOf[method.signature.Input])
          result <- IO.syncThrowable(method.invoke(context, casted))
          safeResult <- result
          encoded <- IO.syncThrowable(method.marshaller.encodeResponse(IRTResBody(safeResult)).noSpaces)
        } yield {
          encoded
        }
      }
    }
  }
}
