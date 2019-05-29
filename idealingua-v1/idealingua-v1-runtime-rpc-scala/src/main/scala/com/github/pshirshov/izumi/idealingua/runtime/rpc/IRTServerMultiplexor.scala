package com.github.pshirshov.izumi.idealingua.runtime.rpc

import com.github.pshirshov.izumi.functional.bio.{BIO, BIOExit}
import com.github.pshirshov.izumi.functional.bio.BIO._
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks
import io.circe.Json


trait ContextExtender[B[+ _, + _], Ctx, Ctx2] {
  def extend(context: Ctx, body: Json): Ctx2
}

object ContextExtender {
  def id[B[+ _, + _], Ctx]: ContextExtender[B, Ctx, Ctx] = new ContextExtender[B, Ctx, Ctx] {
    override def extend(context: Ctx, body: Json): Ctx = {
      Quirks.discard(body)
      context
    }
  }
}


class IRTServerMultiplexor[R[+_, +_] : BIO, C, C2](list: Set[IRTWrappedService[R, C2]], extender: ContextExtender[R, C, C2]) {
  protected val BIO: BIO[R] = implicitly

  val services: Map[IRTServiceId, IRTWrappedService[R, C2]] = list.map(s => s.serviceId -> s).toMap

  def doInvoke(parsedBody: Json, context: C, toInvoke: IRTMethodId): R[Throwable, Option[Json]] = {
    (for {
      service <- services.get(toInvoke.service)
      method <- service.allMethods.get(toInvoke)
    } yield {
      method
    }) match {
      case Some(value) =>
        invoke(extender.extend(context, parsedBody), toInvoke, value, parsedBody).map(Some.apply)
      case None =>
        BIO.pure(None)
    }
  }

  @inline private[this] def invoke(context: C2, toInvoke: IRTMethodId, method: IRTMethodWrapper[R, C2], parsedBody: Json): R[Throwable, Json] = {
    for {
      decodeAction <- BIO.syncThrowable(method.marshaller.decodeRequest[R].apply(IRTJsonBody(toInvoke, parsedBody)))
      safeDecoded <- decodeAction.sandbox.catchAll {
        case BIOExit.Termination(_, exceptions) =>
          BIO.fail(new IRTDecodingException(s"$toInvoke: Failed to decode JSON ${parsedBody.toString()}", exceptions.headOption))
        case BIOExit.Error(decodingFailure) =>
          BIO.fail(new IRTDecodingException(s"$toInvoke: Failed to decode JSON ${parsedBody.toString()}", Some(decodingFailure)))
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

