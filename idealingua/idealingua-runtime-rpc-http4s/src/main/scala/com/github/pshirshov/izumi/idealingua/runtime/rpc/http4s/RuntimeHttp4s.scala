package com.github.pshirshov.izumi.idealingua.runtime.rpc.http4s

import cats._
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

class RuntimeHttp4s[R[_] : IRTServiceResult : Monad](logger: IzLogger = IzLogger.NullLogger) {
  type MaterializedStream = String
  type StreamDecoder = EntityDecoder[R, MaterializedStream]
  private val TM: IRTServiceResult[R] = implicitly[IRTServiceResult[R]]


  protected def loggingMiddle(service: HttpService[R], logger: IzLogger): HttpService[R] = cats.data.Kleisli {
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

  def httpService[Ctx]
  (
    muxer: IRTServerMultiplexor[R, Ctx]
    , contextProvider: AuthMiddleware[R, Ctx]
    , marshallers: IRTServerMarshallers
    , dsl: Http4sDsl[R]
  )(implicit ed: StreamDecoder): HttpService[R] = {

    def requestDecoder(context: Ctx, method: IRTMethod): EntityDecoder[R, muxer.Input] =
      EntityDecoder.decodeBy(MediaRange.`*/*`) {
        message =>
          val decoded: R[Either[DecodeFailure, IRTInContext[IRTMuxRequest[Product], Ctx]]] = message.as[String].map {
            body =>
              logger.trace(s"$method: Going to decode request body $body ($context)")

              marshallers.decodeRequest(body, method).map {
                decoded =>
                  logger.trace(s"$method: request decoded: $decoded ($context)")
                  IRTInContext(IRTMuxRequest(decoded.value, method), context)
              }.leftMap {
                error =>
                  logger.info(s"$method: Cannot decode request body because of circe failure: $body => $error ($context)")
                  InvalidMessageBodyFailure(s"$method: Cannot decode body because of circe failure: $body => $error ($context)", Option(error))
              }
          }

          DecodeResult(decoded)
      }


    def respEncoder(): EntityEncoder[R, muxer.Output] =
      EntityEncoder.encodeBy(headers.`Content-Type`(MediaType.`application/json`)) {
        v =>
          logger.trace(s"Going to encode response $v")
          TM.wrap {
            val s = Stream.emits(marshallers.encodeResponse(v.body).getBytes).covary[R]
            logger.trace(s"Encoded request $v => $s")
            Entity.apply(s)
          }
      }


    import dsl._

    implicit val enc: EntityEncoder[R, muxer.Output] = respEncoder()

    val svc = AuthedService[Ctx, R] {
      case GET -> Root / service / method as ctx =>
        val methodId = IRTMethod(IRTServiceId(service), IRTMethodId(method))
        val req = IRTInContext(IRTMuxRequest[Product](methodId, methodId), ctx)
        TM.flatMap(muxer.dispatch(req))(dsl.Ok(_))


      case request@POST -> Root / service / method as ctx =>
        val methodId = IRTMethod(IRTServiceId(service), IRTMethodId(method))
        implicit val dec: EntityDecoder[R, muxer.Input] = requestDecoder(ctx, methodId)

        request.req.decode[IRTInContext[IRTMuxRequest[Product], Ctx]] {
          message =>
            TM.flatMap(muxer.dispatch(message))(dsl.Ok(_))
        }
    }

    val aservice: HttpService[R] = contextProvider(svc)
    loggingMiddle(aservice, logger)
  }

  def httpClient
  (client: Client[R], marshallers: IRTClientMarshallers)
  (builder: (IRTMuxRequest[Product], EntityBody[R]) => Request[R])
  (implicit ed: StreamDecoder): IRTDispatcher[IRTMuxRequest[Product], IRTMuxResponse[Product], R] = {
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
                logger.trace(s"${input.method -> "method"}: Received response: $body")
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

  def requestBuilder(baseUri: Uri)(input: IRTMuxRequest[Product], body: EntityBody[R]): Request[R] = {
    val uri = baseUri / input.method.service.value / input.method.methodId.value

    if (input.body.value.productArity > 0) {
      Request(org.http4s.Method.POST, uri, body = body)
    } else {
      Request(org.http4s.Method.GET, uri)
    }
  }
}
