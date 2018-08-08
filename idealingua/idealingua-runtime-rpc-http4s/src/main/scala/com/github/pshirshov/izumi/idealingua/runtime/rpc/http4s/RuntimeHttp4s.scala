package com.github.pshirshov.izumi.idealingua.runtime.rpc.http4s

import cats._
import cats.effect.Sync
import cats.implicits._
import com.github.pshirshov.izumi.idealingua.runtime.circe.{IRTClientMarshallers, IRTServerMarshallers}
import com.github.pshirshov.izumi.idealingua.runtime.rpc._
import com.github.pshirshov.izumi.logstage.api.IzLogger
import fs2.Stream
import org.http4s._
import org.http4s.client.Client
import org.http4s.dsl._
import org.http4s.server.AuthMiddleware

import scala.language.higherKinds
import scala.util.{Failure, Success, Try}

trait Http4sContext[R[_]] {
  type MaterializedStream = String
  type StreamDecoder = EntityDecoder[R, MaterializedStream]

  protected def dsl: Http4sDsl[R]

  protected def TM: IRTServiceResult[R]

  protected def logger: IzLogger

  protected implicit def R: Monad[R]

  protected implicit def S: Sync[R]
}

trait WithRequestBuilder[R[_]] {
  def requestBuilder(baseUri: Uri)(input: IRTMuxRequest[Product], body: EntityBody[R]): Request[R] = {
    val uri = baseUri / input.method.service.value / input.method.methodId.value

    if (input.body.value.productArity > 0) {
      Request(org.http4s.Method.POST, uri, body = body)
    } else {
      Request(org.http4s.Method.GET, uri)
    }
  }
}

trait WithHttp4sLoggingMiddleware[R[_]] {
  this: Http4sContext[R] =>


  protected def loggingMiddle(service: HttpRoutes[R]): HttpRoutes[R] = cats.data.Kleisli {
    req: Request[R] =>
      logger.trace(s"${req.method.name -> "method"} ${req.pathInfo -> "path"}")

      try {
        service(req).map {
          case Status.Successful(resp) =>
            logger.debug(s"${req.method.name -> "method"} ${req.pathInfo -> "path"}")
            resp
          case resp =>
            logger.info(s"${req.method.name -> "method"} ${req.pathInfo -> "uri"} => ${resp.status.code -> "code"} ${resp.status.code -> "reason"}")
            resp
        }
      } catch {
        case t: Throwable =>
          logger.error(s"${req.method.name -> "method"} ${req.pathInfo -> "path"}: failed to handle request: $t")
          throw t
      }
  }
}


trait WithHttp4sClient[R[_]] {
  this: Http4sContext[R] =>

  def httpClient
  (client: Client[R], marshallers: IRTClientMarshallers)
  (builder: (IRTMuxRequest[Product], EntityBody[R]) => Request[R])
  : IRTDispatcher[IRTMuxRequest[Product], IRTMuxResponse[Product], R] = {
    new IRTDispatcher[IRTMuxRequest[Product], IRTMuxResponse[Product], R] {
      override def dispatch(input: IRTMuxRequest[Product]): Result[IRTMuxResponse[Product]] = {
        val body = marshallers.encodeRequest(input.body)
        val outBytes: Array[Byte] = body.getBytes
        val entityBody: EntityBody[R] = Stream.emits(outBytes).covary[R]
        val req: Request[R] = builder(input, entityBody)

        logger.trace(s"${input.method -> "method"}: Prepared request $body")
        client.fetch(req) {
          resp =>
            logger.trace(s"${input.method -> "method"}: Received response, going to materialize")
            resp.as[MaterializedStream].map {
              body =>
                logger.trace(s"${input.method -> "method"}: \n\n\t Received response: $body")
                marshallers.decodeResponse(body, input.method).map {
                  product =>
                    logger.trace(s"${input.method -> "method"}: decoded response: $product")
                    IRTMuxResponse(product.value, input.method)
                } match {
                  case Right(v) =>
                    v
                  case Left(f) =>
                    logger.info(s"${input.method -> "method"}: decoder failed on $body: $f")
                    throw new IRTUnparseableDataException(s"${input.method}: decoder failed on $body: $f", Option(f))
                }
            }
        }
      }
    }
  }
}

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

class RuntimeHttp4s[R[_] : IRTServiceResult : Monad : Sync]
(
  override protected val logger: IzLogger
  , override protected val dsl: Http4sDsl[R]
  , override protected val marshallers: IRTServerMarshallers
)
  extends Http4sContext[R]
    with WithHttp4sLoggingMiddleware[R]
    with WithRequestBuilder[R]
    with WithHttp4sClient[R]
    with WithHttp4sServer[R] {
  override protected val TM: IRTServiceResult[R] = implicitly[IRTServiceResult[R]]

  override protected val R: Monad[R] = implicitly[Monad[R]]

  override protected def S: Sync[R] = implicitly


}
