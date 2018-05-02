package com.github.pshirshov.izumi.idealingua.runtime.rpc.http4s

import cats._
import cats.implicits._
import com.github.pshirshov.izumi.idealingua.runtime.circe.{IRTClientMarshallers, IRTServerMarshallers}
import com.github.pshirshov.izumi.idealingua.runtime.rpc._
import com.github.pshirshov.izumi.idealingua.runtime.rpc._
import fs2.Stream
import org.http4s._
import org.http4s.client.Client
import org.http4s.dsl._
import org.http4s.server.AuthMiddleware

import scala.language.higherKinds


class RuntimeHttp4s[R[_] : IRTServiceResult : Monad] {
  type MaterializedStream = String
  type StreamDecoder = EntityDecoder[R, MaterializedStream]
  private val TM: IRTServiceResult[R] = implicitly[IRTServiceResult[R]]



  def httpService[Ctx]
  (
    muxer: IRTServerMultiplexor[R, Ctx]
    , contextProvider: AuthMiddleware[R, Ctx]
    , marshallers: IRTServerMarshallers
    , dsl: Http4sDsl[R]
  )(implicit ed: StreamDecoder): HttpService[R] = {

    def requestDecoder(context: Ctx, m: IRTMethod): EntityDecoder[R, muxer.Input] =
      EntityDecoder.decodeBy(MediaRange.`*/*`) {
        message =>
          val decoded: R[Either[DecodeFailure, IRTInContext[IRTMuxRequest[Product], Ctx]]] = message.as[String].map {
            str =>
              marshallers.decodeRequest(str, m).map {
                body =>
                  IRTInContext(IRTMuxRequest(body.value, m), context)
              }.leftMap {
                error =>
                  InvalidMessageBodyFailure(s"Cannot decode body because of circe failure: $str", Option(error))
              }
          }

          DecodeResult(decoded)
      }


    def respEncoder(): EntityEncoder[R, muxer.Output] =
      EntityEncoder.encodeBy(headers.`Content-Type`(MediaType.`application/json`)) {
        v =>
          TM.wrap {
            val s = Stream.emits(marshallers.encodeResponse(v.body).getBytes).covary[R]
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
    aservice
  }

  def httpClient
  (client: Client[R], marshallers: IRTClientMarshallers)
  (builder: (IRTMuxRequest[Product], EntityBody[R]) => Request[R])
  (implicit ed: StreamDecoder): IRTDispatcher[IRTMuxRequest[Product], IRTMuxResponse[Product], R] = {
    new IRTDispatcher[IRTMuxRequest[Product], IRTMuxResponse[Product], R] {
      override def dispatch(input: IRTMuxRequest[Product]): Result[IRTMuxResponse[Product]] = {
        val outBytes: Array[Byte] = marshallers.encodeRequest(input.body).getBytes
        val body: EntityBody[R] = Stream.emits(outBytes).covary[R]
        val req: Request[R] = builder(input, body)

        client.fetch(req) {
          resp =>
            resp.as[MaterializedStream].map {
              s =>
                marshallers.decodeResponse(s, input.method).map {
                  product =>
                    IRTMuxResponse(product.value, input.method)
                } match {
                  case Right(v) =>
                    v
                  case Left(f) =>
                    throw new IRTUnparseableDataException(s"Decoder failed on $s: $f", Option(f))
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
