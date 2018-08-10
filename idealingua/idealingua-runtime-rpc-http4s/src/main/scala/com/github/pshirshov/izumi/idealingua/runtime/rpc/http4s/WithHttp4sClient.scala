package com.github.pshirshov.izumi.idealingua.runtime.rpc.http4s

import com.github.pshirshov.izumi.idealingua.runtime.rpc._
import fs2.Stream
import org.http4s._
import org.http4s.client._
import org.http4s.client.blaze._
import scalaz.zio.ExitResult

trait WithHttp4sClient {
  this: Http4sContext =>

  //  protected def clientMarshallers: IRTClientMarshallers
  //
  class ClientDispatcher(baseUri: Uri, codec: IRTCodec)
    extends Dispatcher with IRTZioResult {

    private val client: CIO[Client[CIO]] = Http1Client[CIO]()

    def dispatch(input: IRTMuxRequest[Product]): ZIO[Throwable, IRTMuxResponse[Product]] = {
      codec
        .encode(input)
        .flatMap {
          encoded =>
            val outBytes: Array[Byte] = encoded.getBytes
            val entityBody: EntityBody[CIO] = Stream.emits(outBytes).covary[CIO]
            val req = buildRequest(baseUri, input, entityBody)

            logger.trace(s"${input.method -> "method"}: Prepared request $encoded")

            try {
              ZIO.point(client.flatMap(_.fetch(req)(handleResponse(input, _))).unsafeRunSync())
            } catch {
              case t: Throwable =>
                ZIO.terminate(t)
            }
        }

    }

    protected def handleResponse(input: IRTMuxRequest[Product], resp: Response[CIO]): CIO[IRTMuxResponse[Product]] = {
      logger.trace(s"${input.method -> "method"}: Received response, going to materialize")
      if (resp.status == Status.Ok) {
        resp
          .as[MaterializedStream]
          .flatMap {
            body =>
              logger.trace(s"${input.method -> "method"}: Received response: $body")
              val decoded = codec.decode(body, input.method).map {
                product =>
                  logger.trace(s"${input.method -> "method"}: decoded response: $product")
                  product
              }

              ZIOR.unsafeRunSync(decoded) match {
                case ExitResult.Completed(v) =>
                  CIO.pure(v)
                case ExitResult.Failed(error, defects) =>
                  CIO.raiseError(new IRTUnparseableDataException(s"${input.method}: decoder failed on $body: $error", Option(error)))

                case ExitResult.Terminated(causes) =>
                  val f = causes.head
                  logger.info(s"${input.method -> "method"}: decoder failed on $body: $f")
                  CIO.raiseError(new IRTUnparseableDataException(s"${input.method}: decoder failed on $body: $f", Option(f)))
              }
          }
      } else {
        CIO.raiseError(IRTUnexpectedHttpStatus(resp.status))
      }
    }

    protected final def buildRequest(baseUri: Uri, input: IRTMuxRequest[Product], body: EntityBody[CIO]): Request[CIO] = {
      val uri = baseUri / input.method.service.value / input.method.methodId.value

      val base: Request[CIO] = if (input.body.value.productArity > 0) {
        Request(org.http4s.Method.POST, uri, body = body)
      } else {
        Request(org.http4s.Method.GET, uri)
      }

      transformRequest(base)
    }

    protected def transformRequest(request: Request[CIO]): Request[CIO] = request
  }

}
