package com.github.pshirshov.izumi.idealingua.runtime.rpc.http4s

import java.util.concurrent.RejectedExecutionException

import _root_.io.circe.parser._
import cats.implicits._
import com.github.pshirshov.izumi.functional.bio.BIO._
import com.github.pshirshov.izumi.idealingua.runtime.rpc
import com.github.pshirshov.izumi.idealingua.runtime.rpc.{IRTClientMultiplexor, RPCPacketKind, _}
import io.circe
import io.circe.Json
import io.circe.syntax._
import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.http4s.server.AuthMiddleware
import org.http4s.server.websocket.WebSocketBuilder
import org.http4s.websocket.WebSocketFrame
import org.http4s.websocket.WebSocketFrame.{Binary, Close, Text}

import scala.util.Try

trait WithHttp4sServer {
  this: Http4sContext with WithHttp4sLoggingMiddleware with WithHttp4sHttpRequestContext with WithWebsocketClientContext =>

  def server[Ctx, ClientId](muxer: IRTServerMultiplexor[BiIO, Ctx]
                            , codec: IRTClientMultiplexor[BiIO]
                            , contextProvider: AuthMiddleware[CatsIO, Ctx]
                            , wsContextProvider: WsContextProvider[Ctx, ClientId]
                            , wsSessionStorage: WsSessionsStorage[BiIO, ClientId, Ctx]
                            , listeners: Seq[WsSessionListener[ClientId]]): HttpServer[Ctx, ClientId] =
    new HttpServer[Ctx, ClientId](muxer, codec, contextProvider, wsContextProvider, wsSessionStorage, listeners)

  class HttpServer[Ctx, ClientId](
                                   val muxer: IRTServerMultiplexor[BiIO, Ctx]
                                   , val codec: IRTClientMultiplexor[BiIO]
                                   , val contextProvider: AuthMiddleware[CatsIO, Ctx]
                                   , val wsContextProvider: WsContextProvider[Ctx, ClientId]
                                   , val wsSessionStorage: WsSessionsStorage[BiIO, ClientId, Ctx]
                                   , val listeners: Seq[WsSessionListener[ClientId]]
                                 ) {
    protected val dsl: Http4sDsl[CatsIO] = WithHttp4sServer.this.dsl

    import dsl._


    def service: HttpRoutes[CatsIO] = {
      val svc = AuthedService(handler())
      val aservice: HttpRoutes[CatsIO] = contextProvider(svc)
      loggingMiddle(aservice)
    }

    protected def handler(): PartialFunction[AuthedRequest[CatsIO, Ctx], CatsIO[Response[CatsIO]]] = {
      case request@GET -> Root / "ws" as ctx =>
        setupWs(request, ctx)

      case request@GET -> Root / service / method as ctx =>
        val methodId = IRTMethodId(IRTServiceId(service), IRTMethodName(method))
        run(HttpRequestContext(request, ctx), body = "{}", methodId)

      case request@POST -> Root / service / method as ctx =>
        val methodId = IRTMethodId(IRTServiceId(service), IRTMethodName(method))
        request.req.decode[String] {
          body =>
            run(HttpRequestContext(request, ctx), body, methodId)
        }
    }


    protected def setupWs(request: AuthedRequest[CatsIO, Ctx], initialContext: Ctx): CatsIO[Response[CatsIO]] = {
      val context = new WebsocketClientContextImpl[BiIO, ClientId, Ctx](request, initialContext, listeners, wsSessionStorage)
      context.start()
      logger.debug(s"${context -> null}: Websocket client connected")

      context.queue.flatMap { q =>
        val d = q.dequeue.through {
          stream =>
            stream
              .map {
                case m: Close =>
                  logger.debug(s"${context -> null}: Websocket client disconnected")
                  context.finish()
                  m
                case m => m
              }
              .evalMap(handleWsMessage(context))
              .collect({ case Some(v) => WebSocketFrame.Text(v) })
        }
        val e = q.enqueue
        WebSocketBuilder[CatsIO].build(d.merge(context.outStream).merge(context.pingStream), e)
      }
    }

    protected def handleWsMessage(context: WebsocketClientContextImpl[BiIO, ClientId, Ctx]): WebSocketFrame => CatsIO[Option[String]] = {
      case Text(msg, _) =>
        val ioresponse = makeResponse(context, msg)
        CIO.async {
          cb =>
            BIORunner.unsafeRunAsyncAsEither(ioresponse) {
              result =>
                cb(Right(handleResult(context, result)))
            }
        }

      case v: Binary =>
        CIO.point(Some(handleWsError(context, List.empty, Some(v.toString.take(100) + "..."), "badframe")))
    }

    protected def handleResult(context: WebsocketClientContextImpl[BiIO, ClientId, Ctx], result: Try[Either[Throwable, Option[RpcPacket]]]): Option[String] = {
      result match {
        case scala.util.Success(Right(v)) =>
          v.map(_.asJson).map(printer.pretty)

        case scala.util.Success(Left(error)) =>
          Some(handleWsError(context, List(error), None, "failure"))

        case scala.util.Failure(cause) =>
          Some(handleWsError(context, List(cause), None, "termination"))
      }
    }

    protected def makeResponse(context: WebsocketClientContextImpl[BiIO, ClientId, Ctx], message: String): BiIO[Throwable, Option[RpcPacket]] = {
      for {
        parsed <- BIO.fromEither(parse(message))
        unmarshalled <- BIO.fromEither(parsed.as[RpcPacket])
        id <- BIO.syncThrowable(wsContextProvider.toId(context.initialContext, unmarshalled))
        userCtx <- BIO.syncThrowable(wsContextProvider.toContext(context.initialContext, unmarshalled))
        _ <- BIO.syncThrowable(context.updateId(id))
        _ <- BIO.point(logger.debug(s"${context -> null}: $id, $userCtx"))
        response <- respond(context, userCtx, unmarshalled).sandboxWith {
          _.redeem(
            {
              case Left(exception :: otherIssues) =>
                logger.error(s"${context -> null}: WS processing terminated, $message, $exception, $otherIssues")
                BIO.point(Some(rpc.RpcPacket.rpcFail(unmarshalled.id, exception.getMessage)))
              case Left(Nil) =>
                BIO.terminate(new IllegalStateException())
              case Right(exception) =>
                logger.error(s"${context -> null}: WS processing failed, $message, $exception")
                BIO.point(Some(rpc.RpcPacket.rpcFail(unmarshalled.id, exception.getMessage)))
            }, {
              succ => BIO.point(succ)
            }
          )
        }
      } yield {
        response
      }
    }

    protected def handleWsError(context: WebsocketClientContextImpl[BiIO, ClientId, Ctx], causes: List[Throwable], data: Option[String], kind: String): String = {
      causes.headOption match {
        case Some(cause) =>
          logger.error(s"${context -> null}: WS Execution failed, $kind, $data, $cause")
          printer.pretty(rpc.RpcPacket.rpcCritical(data.getOrElse(cause.getMessage), kind).asJson)

        case None =>
          logger.error(s"${context -> null}: WS Execution failed, $kind, $data")
          printer.pretty(rpc.RpcPacket.rpcCritical("?", kind).asJson)
      }
    }

    protected def respond(context: WebsocketClientContextImpl[BiIO, ClientId, Ctx], userContext: Ctx, input: RpcPacket): BiIO[Throwable, Option[RpcPacket]] = {
      input match {
        case RpcPacket(RPCPacketKind.RpcRequest, data, Some(id), _, Some(service), Some(method), _) =>
          val methodId = IRTMethodId(IRTServiceId(service), IRTMethodName(method))
          muxer.doInvoke(data, userContext, methodId).flatMap {
            case None =>
              BIO.fail(new IRTMissingHandlerException(s"${context -> null}: No rpc handler for $methodId", input))
            case Some(resp) =>
              BIO.point(Some(rpc.RpcPacket.rpcResponse(id, resp)))
          }

        case RpcPacket(RPCPacketKind.BuzzResponse, data, _, id, _, _, _) =>
          context.requestState.handleResponse(id, data).as(None)

        case RpcPacket(RPCPacketKind.BuzzFailure, data, _, Some(id), _, _, _) =>
          context.requestState.respond(id, RawResponse.BadRawResponse())
          BIO.fail(new IRTGenericFailure(s"Buzzer has returned failure: $data"))

        case k =>
          BIO.fail(new IRTMissingHandlerException(s"Can't handle $k", k))
      }
    }


    protected def run(context: HttpRequestContext[Ctx], body: String, method: IRTMethodId): CatsIO[Response[CatsIO]] = {
      val ioR = for {
        parsed <- BIO.fromEither(parse(body))
        maybeResult <- muxer.doInvoke(parsed, context.context, method)
      } yield {
        maybeResult
      }

      CIO.async[CatsIO[Response[CatsIO]]] {
        cb =>
          BIORunner.unsafeRunAsyncAsEither(ioR) {
            result =>
              cb(Right(handleResult(context, method, result)))
          }
      }
        .flatten
    }

    private def handleResult(context: HttpRequestContext[Ctx], method: IRTMethodId, result: Try[Either[Throwable, Option[Json]]]): CatsIO[Response[CatsIO]] = {
      result match {
        case scala.util.Success(Right(v)) =>
          v match {
            case Some(value) =>
              dsl.Ok(printer.pretty(value))
            case None =>
              logger.warn(s"${context -> null}: No service handler for $method")
              dsl.NotFound()
          }

        case scala.util.Success(Left(error: circe.Error)) =>
          logger.info(s"${context -> null}: Parsing failure while handling $method: $error")
          dsl.BadRequest()

        case scala.util.Success(Left(error)) =>
          logger.info(s"${context -> null}: Unexpected failure while handling $method: $error")
          dsl.InternalServerError()

        case scala.util.Failure(cause: IRTHttpFailureException) =>
          logger.debug(s"${context -> null}: Request rejected, $method, ${context.request}, $cause")
          CIO.pure(Response(status = cause.status))

        case scala.util.Failure(cause : RejectedExecutionException) =>
          logger.warn(s"${context -> null}: Not enough capacity to handle $method: $cause")
          dsl.TooManyRequests()

        case scala.util.Failure(cause) =>
          logger.error(s"${context -> null}: Execution failed, termination, $method, ${context.request}, $cause")
          dsl.InternalServerError()
      }
    }

  }

}
