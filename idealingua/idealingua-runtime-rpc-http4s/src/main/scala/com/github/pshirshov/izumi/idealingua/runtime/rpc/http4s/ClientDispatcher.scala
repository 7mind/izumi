package com.github.pshirshov.izumi.idealingua.runtime.rpc.http4s

import cats.implicits._
import com.github.pshirshov.izumi.functional.bio.BIO._
import com.github.pshirshov.izumi.functional.bio.BIOExit
import com.github.pshirshov.izumi.idealingua.runtime.rpc._
import com.github.pshirshov.izumi.logstage.api.IzLogger
import fs2.Stream
import io.circe.Printer
import io.circe.parser.parse
import org.http4s._
import org.http4s.client.blaze._

class ClientDispatcher[C <: Http4sContext](val c: C#IMPL[C], logger: IzLogger, printer: Printer, baseUri: Uri, codec: IRTClientMultiplexor[C#BiIO])
  extends IRTDispatcher[C#BiIO] {
  import c._

  def dispatch(request: IRTMuxRequest): BiIO[Throwable, IRTMuxResponse] = {
    logger.trace(s"${request.method -> "method"}: Goint to perform $request")
    val handler = handleResponse(request, _: Response[CatsIO])
    codec
      .encode(request)
      .flatMap {
        encoded =>
          val outBytes: Array[Byte] = printer.pretty(encoded).getBytes
          val req = buildRequest(baseUri, request, outBytes)
          logger.debug(s"${request.method -> "method"}: Prepared request $encoded")
          runRequest(handler, req)
      }
  }

  protected def runRequest[T](handler: Response[CatsIO] => CatsIO[T], req: Request[CatsIO]): BiIO[Throwable, T] = {
    val cio = BlazeClientBuilder[CatsIO](c.clientExecutionContext).resource.use {
      client =>
        client.fetch(req)(handler)
    }
    c.BIO.async(c.CIORunner.unsafeRunAsync(cio))
  }

  protected def handleResponse(input: IRTMuxRequest, resp: Response[CatsIO]): CatsIO[IRTMuxResponse] = {
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
            parsed <- c.BIO.fromEither(parse(body))
            product <- codec.decode(parsed, input.method)
          } yield {
            logger.trace(s"${input.method -> "method"}: decoded response: $product")
            product
          }

          c.CIO.async {
            cb =>
              c.BIORunner.unsafeRunAsyncAsEither(decoded) {
                case BIOExit.Success(v) =>
                  cb(Right(v))

                case BIOExit.Error(error) =>
                  logger.info(s"${input.method -> "method"}: decoder returned failure on $body: $error")
                  cb(Left(new IRTUnparseableDataException(s"${input.method}: decoder returned failure on $body: $error", Option(error))))

                case BIOExit.Termination(f, _) =>
                  logger.info(s"${input.method -> "method"}: decoder failed on $body: $f")
                  cb(Left(new IRTUnparseableDataException(s"${input.method}: decoder failed on $body: $f", Option(f))))
              }
          }
      }
  }

  protected final def buildRequest(baseUri: Uri, input: IRTMuxRequest, body: Array[Byte]): Request[CatsIO] = {
    val entityBody: EntityBody[CatsIO] = Stream.emits(body).covary[CatsIO]
    buildRequest(baseUri, input, entityBody)
  }

  protected final def buildRequest(baseUri: Uri, input: IRTMuxRequest, body: EntityBody[CatsIO]): Request[CatsIO] = {
    val uri = baseUri / input.method.service.value / input.method.methodId.value

    val base: Request[CatsIO] = if (input.body.value.productArity > 0) {
      Request(org.http4s.Method.POST, uri, body = body)
    } else {
      Request(org.http4s.Method.GET, uri)
    }

    transformRequest(base)
  }

  protected def transformRequest(request: Request[CatsIO]): Request[CatsIO] = request
}
