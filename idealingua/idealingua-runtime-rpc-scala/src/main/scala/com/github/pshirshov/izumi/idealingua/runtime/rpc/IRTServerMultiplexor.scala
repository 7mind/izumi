package com.github.pshirshov.izumi.idealingua.runtime.rpc

import io.circe.ParsingFailure
import scalaz.zio.IO

class IRTServerMultiplexor[C](list: Set[IRTWrappedService[IO, C]])
  extends IRTZioResult {
  val services: Map[IRTServiceId, IRTWrappedService[IO, C]] = list.map(s => s.serviceId -> s).toMap


  def doInvoke(body: String, context: C, toInvoke: IRTMethodId): Either[ParsingFailure, Option[IO[Throwable, String]]] = {
    for {
      parsed <- _root_.io.circe.parser.parse(body)
    } yield {
      for {
        service <- services.get(toInvoke.service)
        method <- service.allMethods.get(toInvoke)
      } yield {
        for {
          decoded <- method.marshaller.decodeRequest(IRTJsonBody(toInvoke, parsed))
          casted <- IO.syncThrowable(decoded.value.asInstanceOf[method.signature.Input])
          result <- IO.syncThrowable(method.invoke(context, casted))
          safeResult <- toZio(result)
          encoded <- IO.syncThrowable(method.marshaller.encodeResponse(IRTResBody(safeResult)).noSpaces)
        } yield {
          encoded
        }
      }
    }
  }
}
