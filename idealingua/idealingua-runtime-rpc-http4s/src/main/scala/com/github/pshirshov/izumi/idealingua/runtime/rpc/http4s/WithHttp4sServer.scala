package com.github.pshirshov.izumi.idealingua.runtime.rpc.http4s

import cats.implicits._
import com.github.pshirshov.izumi.idealingua.runtime.circe.IRTServerMarshallers
import com.github.pshirshov.izumi.idealingua.runtime.rpc._
import fs2.Stream
import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.http4s.server.AuthMiddleware

import scala.language.higherKinds
import scala.util.{Failure, Success, Try}

trait WithHttp4sServer[R[_]] {
  this: Http4sContext[R] with WithHttp4sLoggingMiddleware[R] =>

  protected def serverMarshallers: IRTServerMarshallers

  class HttpServer[Ctx](
                         protected val muxer: IRTServerMultiplexor[R, Ctx]
                         , protected val contextProvider: AuthMiddleware[R, Ctx]
                       ) {
    protected val dsl: Http4sDsl[R] = WithHttp4sServer.this.dsl

    import dsl._

    def service: HttpRoutes[R] = {
      val svc = AuthedService(wrapped(handler(muxer)))
      val aservice: HttpRoutes[R] = contextProvider(svc)
      loggingMiddle(aservice)
    }

    protected def handler(muxer: IRTServerMultiplexor[R, Ctx]): PartialFunction[AuthedRequest[R, Ctx], R[Response[R]]] = {
      case GET -> Root / service / method as ctx =>
        val methodId = IRTMethod(IRTServiceId(service), IRTMethodId(method))
        val decodedRequest = IRTInContext(IRTMuxRequest[Product](methodId, methodId), ctx)
        run(muxer, decodedRequest)

      case request@POST -> Root / service / method as ctx =>
        val methodId = IRTMethod(IRTServiceId(service), IRTMethodId(method))
        implicit val dec: EntityDecoder[R, muxer.Input] = requestDecoder(ctx, methodId)
        request.req.decode[IRTInContext[IRTMuxRequest[Product], Ctx]] {
          decodedRequest =>
            run(muxer, decodedRequest)
        }
    }

    protected def run(muxer: IRTServerMultiplexor[R, Ctx], request: IRTInContext[IRTMuxRequest[Product], Ctx]): R[Response[R]] = {
      implicit val enc: EntityEncoder[R, muxer.Output] = respEncoder(request.context, request.value.method)
      TM.flatMap(muxer.dispatch(request)) {
        case Right(resp) =>
          dsl.Ok(resp)
        case Left(DispatchingFailure.NoHandler) =>
          logger.warn(s"No handler found for $request")
          dsl.NotFound()
        case Left(DispatchingFailure.Rejected) =>
          logger.info(s"Unautorized $request")
          dsl.Forbidden()
        case Left(DispatchingFailure.Thrown(t)) =>
          logger.warn(s"Failure while handling $request: $t")
          dsl.InternalServerError()
      }
    }

    protected def wrapped(pf: PartialFunction[AuthedRequest[R, Ctx], R[Response[R]]]): PartialFunction[AuthedRequest[R, Ctx], R[Response[R]]] = {
      case arg: AuthedRequest[R, Ctx] =>
        Try(pf(arg)) match {
          case Success(value) =>
            value
          case Failure(_: MatchError) =>
            logger.warn(s"Unexpected: No handler found for ${arg -> "request"}")
            dsl.NotFound()
          case Failure(exception) =>
            logger.warn(s"Unexpected: Failure while handling ${arg -> "request"}: $exception")
            dsl.InternalServerError().map(_.withEntity(s"Unexpected processing failure: ${exception.getMessage}"))
        }
    }

    protected def requestDecoder(context: Ctx, method: IRTMethod): EntityDecoder[R, muxer.Input] = {
      EntityDecoder.decodeBy(MediaRange.`*/*`) {
        message =>
          val decoded: R[Either[DecodeFailure, IRTInContext[IRTMuxRequest[Product], Ctx]]] = message.as[String].map {
            body =>
              logger.trace(s"$method: Going to decode request body $body ($context)")

              serverMarshallers.decodeRequest(body, method).map {
                decoded =>
                  logger.trace(s"$method: request $decoded ($context)")
                  IRTInContext(IRTMuxRequest(decoded.value, method), context)
              }.leftMap {
                error =>
                  logger.info(s"$method: Cannot decode request body because of circe failure: $body => $error ($context)")
                  InvalidMessageBodyFailure(s"$method: Cannot decode body because of circe failure: $body => $error ($context)", Option(error))
              }
          }

          DecodeResult(decoded)
      }
    }


    protected def respEncoder(context: Ctx, method: IRTMethod): EntityEncoder[R, muxer.Output] = {

      EntityEncoder.encodeBy(headers.`Content-Type`(MediaType.application.json)) {
        response =>
          logger.trace(s"$method: Going to encode $response ($context)")
          val encoded = Stream.emits(serverMarshallers.encodeResponse(response.body).getBytes).covary[R]
          logger.trace(s"$method: response $encoded ($context)")
          Entity.apply(encoded)
      }
    }
  }


}
