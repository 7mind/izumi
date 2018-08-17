package com.github.pshirshov.izumi.idealingua.runtime.rpc

import io.circe.Json
import scalaz.zio.IO

import scala.language.higherKinds

class IRTServerMultiplexor[R[_, _], C](list: Set[IRTWrappedService[R, C]])
  extends IRTResultZio {
  val services: Map[IRTServiceId, IRTWrappedService[R, C]] = list.map(s => s.serviceId -> s).toMap


  def doInvoke(parsedBody: Json, context: C, toInvoke: IRTMethodId): IO[Throwable, Option[Json]] = {
    (for {
      service <- services.get(toInvoke.service)
      method <- service.allMethods.get(toInvoke)
    } yield {
      method
    }) match {
      case Some(value) =>
        toM(parsedBody, context, toInvoke, value).map(Some.apply)
      case None =>
        IO.point(None)
    }
  }

  private def toM(parsedBody: Json, context: C, toInvoke: IRTMethodId, method: IRTMethodWrapper[R, C]) = {
    for {
      decoded <- method.marshaller.toZio(method.marshaller.decodeRequest(IRTJsonBody(toInvoke, parsedBody)))
      casted <- IO.syncThrowable(decoded.value.asInstanceOf[method.signature.Input])
      result <- IO.syncThrowable(method.invoke(context, casted))
      safeResult <- method.marshaller.toZio(result)
      encoded <- IO.syncThrowable(method.marshaller.encodeResponse(IRTResBody(safeResult)))
    } yield {
      encoded
    }
  }
}
