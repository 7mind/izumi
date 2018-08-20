package com.github.pshirshov.izumi.idealingua.runtime.rpc

import io.circe.Json
import scalaz.zio.IO

import scala.language.higherKinds

class IRTServerMultiplexor[R[+_, +_] : IRTResultTransZio, C](list: Set[IRTWrappedService[R, C]]) {
  protected val ZIO: IRTResultTransZio[R] = implicitly[IRTResultTransZio[R]]

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
      decoded <- ZIO.toZio(method.marshaller.decodeRequest(IRTJsonBody(toInvoke, parsedBody)))
      casted <- IO.syncThrowable(decoded.value.asInstanceOf[method.signature.Input])
      result <- IO.syncThrowable(method.invoke(context, casted))
      safeResult <- ZIO.toZio(result)
      encoded <- IO.syncThrowable(method.marshaller.encodeResponse(IRTResBody(safeResult)))
    } yield {
      encoded
    }
  }
}
