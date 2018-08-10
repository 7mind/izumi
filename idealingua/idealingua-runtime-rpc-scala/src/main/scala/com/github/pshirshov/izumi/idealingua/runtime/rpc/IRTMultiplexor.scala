package com.github.pshirshov.izumi.idealingua.runtime.rpc

import io.circe.ParsingFailure
import io.circe.parser._
import scalaz.zio.IO

class IRTCodec(codecs: Map[IRTMethodId, IRTMarshaller]) extends IRTZioResult {
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

object IRTCodec {
  def make(services: Map[IRTServiceId, IRTWrappedService[_]]): IRTCodec = {
    new IRTCodec(services.values.flatMap(_.allCodecs).toMap)
  }
}

class IRTMultiplexor[C](list: Set[IRTWrappedService[C]]) extends IRTZioResult {
  val services: Map[IRTServiceId, IRTWrappedService[C]] = list.map(s => s.serviceId -> s).toMap


  def doInvoke(body: String, context: C, toInvoke: IRTMethodId): Either[ParsingFailure, Option[IO[Throwable, String]]] = {
    val invoked =
      _root_.io.circe.parser.parse(body).map {
        parsed =>

          val handlers = for {
            service <- services.get(toInvoke.service)
            codec <- service.allCodecs.get(toInvoke)
            method <- service.allMethods.get(toInvoke)
          } yield {
            (codec, method)
          }

          handlers.map {
            case (codec, method) =>
              codec.decodeRequest
                .apply(IRTRawCall(toInvoke, parsed))
                .flatMap {
                  request =>
                    IO.syncThrowable(request.value.asInstanceOf[method.Input])
                }
                .flatMap {
                  request =>
                    IO.syncThrowable(method.invoke(context, request))
                }
                .flatMap {
                  v =>
                    v
                }
                .flatMap {
                  output =>
                    IO.syncThrowable(codec.encodeResponse.apply(IRTResBody(output)).noSpaces)
                }
          }
      }
    invoked
  }
}
