package com.github.pshirshov.izumi.idealingua.runtime.rpc

import com.github.pshirshov.izumi.functional.bio.BIO
import com.github.pshirshov.izumi.functional.bio.BIO._
import io.circe.Json

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
        invoke(context, toInvoke, value, parsedBody).map(Some.apply)
      case None =>
        R.point(None)
    }
  }

  @inline private[this] def invoke(context: C, toInvoke: IRTMethodId, method: IRTMethodWrapper[R, C], parsedBody: Json): R[Throwable, Json] = {
    for {
      decoded <- R.sandboxWith(method.marshaller.decodeRequest[R].apply(IRTJsonBody(toInvoke, parsedBody))) {
        _.redeem(
          {
            case Left(exceptions) =>
              R.fail(Right(new IRTDecodingException(s"$toInvoke: Failed to decode JSON ${parsedBody.toString()}", exceptions.headOption)))
            case Right(_) => // impossible case, v is Nothing, exhaustive match check fails
              R.terminate(new IllegalStateException())
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
