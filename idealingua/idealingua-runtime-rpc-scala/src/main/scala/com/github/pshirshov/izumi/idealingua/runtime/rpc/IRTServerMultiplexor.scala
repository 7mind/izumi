package com.github.pshirshov.izumi.idealingua.runtime.rpc

import com.github.pshirshov.izumi.idealingua.runtime.bio.BIO
import io.circe.Json
import BIO._
import com.github.pshirshov.izumi.idealingua.runtime.rpc

import scala.language.higherKinds

class IRTServerMultiplexor[R[+_, +_] : BIO, C](list: Set[IRTWrappedService[R, C]]) {
  protected val R: BIO[R] = implicitly

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
      decoded <- R.sandboxWith(method.marshaller.decodeRequest[R].apply(IRTJsonBody(toInvoke, parsedBody))) {
        _.redeem(
          {
            case Left(exception :: _) =>
              R.fail(Right(exception))
            case Left(Nil) =>
              R.terminate(new IllegalStateException())
            case Right(v) =>
              R.terminate(v) // impossible case, v is Nothing, exhaustive match check fails
          }, {
            succ => R.point(succ)
          }
        )
      }
      casted <- R.syncThrowable(decoded.value.asInstanceOf[method.signature.Input])
      result <- R.syncThrowable(method.invoke(context, casted))
      safeResult <- result
      encoded <- R.syncThrowable(method.marshaller.encodeResponse(IRTResBody(safeResult)))
    } yield {
      encoded
    }
  }
}
