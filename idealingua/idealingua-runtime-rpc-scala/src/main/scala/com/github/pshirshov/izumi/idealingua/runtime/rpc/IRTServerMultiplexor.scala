package com.github.pshirshov.izumi.idealingua.runtime.rpc

import com.github.pshirshov.izumi.functional.bio.BIO
import com.github.pshirshov.izumi.functional.bio.BIO._
import io.circe.Json

class IRTServerMultiplexor[R[+_, +_] : BIO, C](list: Set[IRTWrappedService[R, C]]) {
  protected val BIO: BIO[R] = implicitly

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
        BIO.now(None)
    }
  }

  @inline private[this] def invoke(context: C, toInvoke: IRTMethodId, method: IRTMethodWrapper[R, C], parsedBody: Json): R[Throwable, Json] = {
    for {
      decodeAction <- BIO.syncThrowable(method.marshaller.decodeRequest[R].apply(IRTJsonBody(toInvoke, parsedBody)))
      safeDecoded <- BIO.sandboxWith(decodeAction) {
        _.catchAll {
          case Left(exceptions) =>
            BIO.fail(Right(new IRTDecodingException(s"$toInvoke: Failed to decode JSON ${parsedBody.toString()}", exceptions.headOption)))
          case Right(decodingFailure) =>
            BIO.fail(Right(new IRTDecodingException(s"$toInvoke: Failed to decode JSON ${parsedBody.toString()}", Some(decodingFailure))))
        }
      }
      casted <- BIO.syncThrowable(safeDecoded.value.asInstanceOf[method.signature.Input])
      resultAction <- BIO.syncThrowable(method.invoke(context, casted))
      safeResult <- resultAction
      encoded <- BIO.syncThrowable(method.marshaller.encodeResponse.apply(IRTResBody(safeResult)))
    } yield {
      encoded
    }
  }
}
