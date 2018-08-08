package com.github.pshirshov.izumi.idealingua.runtime.rpc.http4s

import cats.implicits._
import com.github.pshirshov.izumi.idealingua.runtime.circe.IRTServerMarshallers
import com.github.pshirshov.izumi.idealingua.runtime.rpc._
import fs2.Stream
import org.http4s._
import org.http4s.server.AuthMiddleware

import scala.language.higherKinds
import scala.util.{Failure, Success, Try}

trait WithHttp4sServer[R[_]] {
  this: Http4sContext[R] with WithHttp4sLoggingMiddleware[R] =>

  protected def marshallers: IRTServerMarshallers


  implicit class MuxerExt[Ctx](val muxer: IRTServerMultiplexor[R, Ctx]) {
    def requestDecoder(context: Ctx, method: IRTMethod): EntityDecoder[R, muxer.Input] =
      EntityDecoder.decodeBy(MediaRange.`*/*`) {
        message =>
          val decoded: R[Either[DecodeFailure, IRTInContext[IRTMuxRequest[Product], Ctx]]] = message.as[String].map {
            body =>
              logger.trace(s"$method: Going to decode request body $body ($context)")

              marshallers.decodeRequest(body, method).map {
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


    def respEncoder(context: Ctx, method: IRTMethod): EntityEncoder[R, muxer.Output] =
      EntityEncoder.encodeBy(headers.`Content-Type`(MediaType.application.json)) {
        response =>
          logger.trace(s"$method: Going to encode $response ($context)")
          val encoded = Stream.emits(marshallers.encodeResponse(response.body).getBytes).covary[R]
          logger.trace(s"$method: response $encoded ($context)")
          Entity.apply(encoded)
      }

  }

  val xdsl = dsl

  import xdsl._

  protected def run[Ctx](muxer: IRTServerMultiplexor[R, Ctx], req: IRTInContext[IRTMuxRequest[Product], Ctx]): R[Response[R]] = {
    implicit val enc: EntityEncoder[R, muxer.Output] = muxer.respEncoder(req.context, req.value.method)
    TM.flatMap(muxer.dispatch(req)) {
      resp =>
        dsl.Ok(resp)
    }
  }

  protected def handler[Ctx](muxer: IRTServerMultiplexor[R, Ctx]): PartialFunction[AuthedRequest[R, Ctx], R[Response[R]]] = {
    case GET -> Root / service / method as ctx =>
      val methodId = IRTMethod(IRTServiceId(service), IRTMethodId(method))
      val decodedRequest = IRTInContext(IRTMuxRequest[Product](methodId, methodId), ctx)
      run(muxer, decodedRequest)

    case request@POST -> Root / service / method as ctx =>
      val methodId = IRTMethod(IRTServiceId(service), IRTMethodId(method))
      implicit val dec: EntityDecoder[R, muxer.Input] = muxer.requestDecoder(ctx, methodId)
      request.req.decode[IRTInContext[IRTMuxRequest[Product], Ctx]] {
        decodedRequest =>
          run(muxer, decodedRequest)
      }
  }

  def wrapped[Ctx](pf: PartialFunction[AuthedRequest[R, Ctx], R[Response[R]]]): PartialFunction[AuthedRequest[R, Ctx], R[Response[R]]] = {
    case arg: AuthedRequest[R, Ctx] =>
      Try(pf(arg)) match {
        case Success(value) =>
          value
        case Failure(_ : MatchError) =>
          dsl.NotFound()
        case Failure(exception) =>
          throw exception
      }
  }

  def httpService[Ctx]
  (
    muxer: IRTServerMultiplexor[R, Ctx]
    , contextProvider: AuthMiddleware[R, Ctx]
  ): HttpRoutes[R] = {
    val svc = AuthedService(wrapped(handler[Ctx](muxer)))
    val aservice: HttpRoutes[R] = contextProvider(svc)
    loggingMiddle(aservice)
  }

}
