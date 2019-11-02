package izumi.idealingua.runtime.rpc

import izumi.functional.bio.{BIO, BIOExit}
import izumi.fundamentals.platform.language.Quirks
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

class IRTServerMultiplexor[F[+_, +_] : BIO, C, C2](list: Set[IRTWrappedService[F, C2]], extender: ContextExtender[F, C, C2]) {
  protected val BIO: BIO[F] = implicitly

  val services: Map[IRTServiceId, IRTWrappedService[F, C2]] = list.map(s => s.serviceId -> s).toMap

  def doInvoke(parsedBody: Json, context: C, toInvoke: IRTMethodId): F[Throwable, Option[Json]] = {
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

  @inline private[this] def invoke(context: C2, toInvoke: IRTMethodId, method: IRTMethodWrapper[F, C2], parsedBody: Json): F[Throwable, Json] = {
    for {
      decodeAction <- BIO.syncThrowable(method.marshaller.decodeRequest[F].apply(IRTJsonBody(toInvoke, parsedBody)))
      safeDecoded <- decodeAction.sandbox.catchAll {
        case BIOExit.Termination(_, exceptions, trace) =>
          BIO.fail(new IRTDecodingException(s"$toInvoke: Failed to decode JSON ${parsedBody.toString()} $trace", exceptions.headOption))
        case BIOExit.Error(decodingFailure, trace) =>
          BIO.fail(new IRTDecodingException(s"$toInvoke: Failed to decode JSON ${parsedBody.toString()} $trace", Some(decodingFailure)))
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
