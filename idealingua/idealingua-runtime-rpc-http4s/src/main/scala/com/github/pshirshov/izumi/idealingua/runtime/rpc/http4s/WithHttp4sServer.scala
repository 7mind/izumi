package com.github.pshirshov.izumi.idealingua.runtime.rpc.http4s

import com.github.pshirshov.izumi.idealingua.runtime.rpc._
import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.http4s.server.AuthMiddleware
import scalaz.zio.{ExitResult, IO, RTS}

import scala.language.higherKinds

trait WithHttp4sServer {
  this: Http4sContext with WithHttp4sLoggingMiddleware =>

  //  protected def serverMarshallers: IRTServerMarshallers

  class HttpServer[Ctx](
                         protected val muxer: IRTMultiplexor[Ctx]
                         , protected val contextProvider: AuthMiddleware[CIO, Ctx]
                       ) {
    protected val dsl: Http4sDsl[CIO] = WithHttp4sServer.this.dsl

    import dsl._

    def service: HttpRoutes[CIO] = {
      val svc = AuthedService(handler())
      val aservice: HttpRoutes[CIO] = contextProvider(svc)
      loggingMiddle(aservice)
    }

    protected def handler(): PartialFunction[AuthedRequest[CIO, Ctx], CIO[Response[CIO]]] = {
      case request@GET -> Root / service / method as ctx =>
        val methodId = IRTMethodId(IRTServiceId(service), IRTMethodName(method))
        run(request, "{}", ctx, methodId)

      case request@POST -> Root / service / method as ctx =>
        val methodId = IRTMethodId(IRTServiceId(service), IRTMethodName(method))
        request.req.decode[String] {
          body =>
            run(request, body, ctx, methodId)
        }
    }

    protected def run(request: AuthedRequest[CIO, Ctx] , body: String, context: Ctx, toInvoke: IRTMethodId): CIO[Response[CIO]] = {
      muxer.doInvoke(body, context, toInvoke) match {
        case Right(Some(value)) =>
          ZIOR.unsafeRunSync(value) match {
            case ExitResult.Completed(v) =>
              dsl.Ok(v)
            case ExitResult.Failed(error, defects) =>
              logger.warn(s"Failure while handling $request: $error")
              dsl.InternalServerError()
            case ExitResult.Terminated(causes) =>
              logger.warn(s"Termination while handling $request: ${causes.head}")
              dsl.InternalServerError()
          }
        case Right(None) =>
          logger.trace(s"No handler for $request")
          dsl.NotFound()

        case Left(e) =>
          logger.trace(s"Parsing failure while handling $request: $e")
          dsl.BadRequest()

      }
    }

    //
    //    protected def run(muxer: IRTServerMultiplexor[R, Ctx], request: IRTInContext[IRTMuxRequest[Product], Ctx]): R[Response[R]] = {
    //      implicit val enc: EntityEncoder[R, muxer.Output] = respEncoder(request.context, request.value.method)
    //      TM.flatMap(muxer.dispatch(request)) {
    //        case Right(resp) =>
    //          dsl.Ok(resp)
    //        case Left(DispatchingFailure.NoHandler) =>
    //          logger.warn(s"No handler found for $request")
    //          dsl.NotFound()
    //        case Left(DispatchingFailure.Rejected) =>
    //          logger.info(s"Unautorized $request")
    //          dsl.Forbidden()
    //        case Left(DispatchingFailure.Thrown(t)) =>
    //          logger.warn(s"Failure while handling $request: $t")
    //          dsl.InternalServerError()
    //      }
    //    }
    //
    //    protected def wrapped(pf: PartialFunction[AuthedRequest[R, Ctx], R[Response[R]]]): PartialFunction[AuthedRequest[R, Ctx], R[Response[R]]] = {
    //      case arg: AuthedRequest[R, Ctx] =>
    //        Try(pf(arg)) match {
    //          case Success(value) =>
    //            value
    //          case Failure(_: MatchError) =>
    //            logger.warn(s"Unexpected: No handler found for ${arg -> "request"}")
    //            dsl.NotFound()
    //          case Failure(exception) =>
    //            logger.warn(s"Unexpected: Failure while handling ${arg -> "request"}: $exception")
    //            dsl.InternalServerError().map(_.withEntity(s"Unexpected processing failure: ${exception.getMessage}"))
    //        }
    //    }
    //
    //    protected def requestDecoder(context: Ctx, method: IRTMethodId): EntityDecoder[R, muxer.Input] = {
    //      EntityDecoder.decodeBy(MediaRange.`*/*`) {
    //        message =>
    //          val decoded: R[Either[DecodeFailure, IRTInContext[IRTMuxRequest[Product], Ctx]]] = message.as[String].map {
    //            body =>
    //              logger.trace(s"$method: Going to decode request body $body ($context)")
    //
    //              serverMarshallers.decodeRequest(body, method).map {
    //                decoded =>
    //                  logger.trace(s"$method: request $decoded ($context)")
    //                  IRTInContext(IRTMuxRequest(decoded.value, method), context)
    //              }.leftMap {
    //                error =>
    //                  logger.info(s"$method: Cannot decode request body because of circe failure: $body => $error ($context)")
    //                  InvalidMessageBodyFailure(s"$method: Cannot decode body because of circe failure: $body => $error ($context)", Option(error))
    //              }
    //          }
    //
    //          DecodeResult(decoded)
    //      }
    //    }
    //
    //
    //    protected def respEncoder(context: Ctx, method: IRTMethodId): EntityEncoder[R, muxer.Output] = {
    //
    //      EntityEncoder.encodeBy(headers.`Content-Type`(MediaType.application.json)) {
    //        response =>
    //          logger.trace(s"$method: Going to encode $response ($context)")
    //          val encoded = Stream.emits(serverMarshallers.encodeResponse(response.body).getBytes).covary[R]
    //          logger.trace(s"$method: response $encoded ($context)")
    //          Entity.apply(encoded)
    //      }
    //    }
  }


}
