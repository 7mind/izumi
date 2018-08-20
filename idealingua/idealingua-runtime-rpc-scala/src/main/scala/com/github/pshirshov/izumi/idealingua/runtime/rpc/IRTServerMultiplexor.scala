package com.github.pshirshov.izumi.idealingua.runtime.rpc

import io.circe.Json
import IRTResult._

import scala.language.higherKinds

class IRTServerMultiplexor[R[+_, +_] : IRTResult, C](list: Set[IRTWrappedService[R, C]]) {
  protected val R: IRTResult[R] = implicitly[IRTResult[R]]

  val services: Map[IRTServiceId, IRTWrappedService[R, C]] = list.map(s => s.serviceId -> s).toMap


  def doInvoke(parsedBody: Json, context: C, toInvoke: IRTMethodId): R[Throwable, Option[Json]] = {
    (for {
      service <- services.get(toInvoke.service)
      method <- service.allMethods.get(toInvoke)
    } yield {
      method
    }) match {
      case Some(value) =>
        toM(parsedBody, context, toInvoke, value).map(Some.apply)
      case None =>
        R.point(None)
    }
  }

  private def toM(parsedBody: Json, context: C, toInvoke: IRTMethodId, method: IRTMethodWrapper[R, C]): R[Throwable, Json] = {
    for {
      decoded <- method.marshaller.decodeRequest(IRTJsonBody(toInvoke, parsedBody))
      casted <- R.syncThrowable(decoded.value.asInstanceOf[method.signature.Input])
      result <- R.syncThrowable(method.invoke(context, casted))
      safeResult <- result
      encoded <- R.syncThrowable(method.marshaller.encodeResponse(IRTResBody(safeResult)))
    } yield {
      encoded
    }
  }
}
