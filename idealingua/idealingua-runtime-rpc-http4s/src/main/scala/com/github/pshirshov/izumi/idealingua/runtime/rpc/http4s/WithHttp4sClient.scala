package com.github.pshirshov.izumi.idealingua.runtime.rpc.http4s

import com.github.pshirshov.izumi.idealingua.runtime.rpc._
import fs2.Stream
import io.circe.parser.parse
import org.http4s._
import org.http4s.client._
import org.http4s.client.blaze._
import scalaz.zio.{ExitResult, IO}

trait WithHttp4sClient {
  this: Http4sContext =>

  def client(baseUri: Uri, codec: IRTClientMultiplexor[BIO]): ClientDispatcher = new ClientDispatcher(baseUri, codec)

  class ClientDispatcher(baseUri: Uri, codec: IRTClientMultiplexor[BIO])
    extends IRTDispatcher with IRTResultZio {

    private val client: CIO[Client[CIO]] = Http1Client[CIO]()

    def dispatch(request: IRTMuxRequest): ZIO[Throwable, IRTMuxResponse] = {
      logger.trace(s"${request.method -> "method"}: Goint to perform $request")

      codec
        .encode(request)
        .flatMap {
          encoded =>
            val outBytes: Array[Byte] = encode(encoded).getBytes
            val entityBody: EntityBody[CIO] = Stream.emits(outBytes).covary[CIO]
            val req = buildRequest(baseUri, request, entityBody)

            logger.debug(s"${request.method -> "method"}: Prepared request $encoded")

            ZIO.syncThrowable {
              client
                .flatMap(_.fetch(req)(handleResponse(request, _)))
                .unsafeRunSync()
            }
        }
    }

    protected def handleResponse(input: IRTMuxRequest, resp: Response[CIO]): CIO[IRTMuxResponse] = {
      logger.trace(s"${input.method -> "method"}: Received response, going to materialize, ${resp.status.code -> "code"} ${resp.status.reason -> "reason"}")

      if (resp.status != Status.Ok) {
        logger.info(s"${input.method -> "method"}: unexpected HTTP response, ${resp.status.code -> "code"} ${resp.status.reason -> "reason"}")
        return CIO.raiseError(IRTUnexpectedHttpStatus(resp.status))
      }

      resp
        .as[MaterializedStream]
        .flatMap {
          body =>
            logger.trace(s"${input.method -> "method"}: Received response: $body")
            val decoded = for {
              parsed <- IO.fromEither(parse(body))
              product <- codec.decode(parsed, input.method)
            } yield {
              logger.trace(s"${input.method -> "method"}: decoded response: $product")
              product
            }

            ZIOR.unsafeRunSync(decoded) match {
              case ExitResult.Completed(v) =>
                CIO.pure(v)

              case ExitResult.Failed(error, _) =>
                logger.info(s"${input.method -> "method"}: decoder failed on $body: $error")
                CIO.raiseError(new IRTUnparseableDataException(s"${input.method}: decoder failed on $body: $error", Option(error)))

              case ExitResult.Terminated(causes) =>
                val f = causes.head
                logger.info(s"${input.method -> "method"}: decoder failed on $body: $f")
                CIO.raiseError(new IRTUnparseableDataException(s"${input.method}: decoder failed on $body: $f", Option(f)))
            }
        }
    }

    protected final def buildRequest(baseUri: Uri, input: IRTMuxRequest, body: EntityBody[CIO]): Request[CIO] = {
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
