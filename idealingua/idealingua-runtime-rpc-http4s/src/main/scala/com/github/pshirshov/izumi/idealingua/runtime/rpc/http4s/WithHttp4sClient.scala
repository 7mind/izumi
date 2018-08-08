package com.github.pshirshov.izumi.idealingua.runtime.rpc.http4s

import cats.implicits._
import com.github.pshirshov.izumi.idealingua.runtime.circe.IRTClientMarshallers
import com.github.pshirshov.izumi.idealingua.runtime.rpc.{IRTDispatcher, IRTMuxRequest, IRTMuxResponse, IRTUnparseableDataException}
import fs2.Stream
import org.http4s._
import org.http4s.client.Client
import org.http4s.client.blaze.Http1Client

import scala.language.higherKinds

trait WithHttp4sClient[R[_]] {
  this: Http4sContext[R] =>

  protected def clientMarshallers: IRTClientMarshallers

  class ClientDispatcher(baseUri: Uri)
    extends IRTDispatcher[IRTMuxRequest[Product], IRTMuxResponse[Product], R] {

    private val client: R[Client[R]] = Http1Client[R]()

    override def dispatch(input: IRTMuxRequest[Product]): Result[IRTMuxResponse[Product]] = {
      val body = clientMarshallers.encodeRequest(input.body)
      val outBytes: Array[Byte] = body.getBytes
      val entityBody: EntityBody[R] = Stream.emits(outBytes).covary[R]
      val req = buildRequest(baseUri, input, entityBody)

      logger.trace(s"${input.method -> "method"}: Prepared request $body")

      client.flatMap(_.fetch(req)(handleResponse(input, _)))
    }

    protected def handleResponse(input: IRTMuxRequest[Product], resp: Response[R]): R[IRTMuxResponse[Product]] = {
      logger.trace(s"${input.method -> "method"}: Received response, going to materialize")
      if (resp.status == Status.Ok) {
        resp
          .as[MaterializedStream]
          .flatMap {
            body =>
              logger.trace(s"${input.method -> "method"}: Received response: $body")
              clientMarshallers.decodeResponse(body, input.method).map {
                product =>
                  logger.trace(s"${input.method -> "method"}: decoded response: $product")
                  IRTMuxResponse(product.value, input.method)
              } match {
                case Right(v) =>
                  E.pure(v)
                case Left(f) =>
                  logger.info(s"${input.method -> "method"}: decoder failed on $body: $f")
                  E.raiseError(new IRTUnparseableDataException(s"${input.method}: decoder failed on $body: $f", Option(f)))
              }
          }
      } else {
        E.raiseError(IRTHttpFailureException(s"Unexpected HTTP status: ${resp.status}", resp.status, None))
      }
    }

    protected final def buildRequest(baseUri: Uri, input: IRTMuxRequest[Product], body: EntityBody[R]): Request[R] = {
      val uri = baseUri / input.method.service.value / input.method.methodId.value

      val base: Request[R] = if (input.body.value.productArity > 0) {
        Request(org.http4s.Method.POST, uri, body = body)
      } else {
        Request(org.http4s.Method.GET, uri)
      }

      transformRequest(base)
    }

    protected def transformRequest(request: Request[R]): Request[R] = request
  }

}
