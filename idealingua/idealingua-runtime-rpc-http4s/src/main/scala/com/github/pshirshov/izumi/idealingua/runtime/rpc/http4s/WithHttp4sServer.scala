package com.github.pshirshov.izumi.idealingua.runtime.rpc.http4s

import java.util.concurrent.ConcurrentHashMap

import _root_.io.circe.parser._
import com.github.pshirshov.izumi.idealingua.runtime.rpc
import com.github.pshirshov.izumi.idealingua.runtime.rpc.{IRTClientMultiplexor, RPCPacketKind, _}
import io.circe.syntax._
import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.http4s.server.AuthMiddleware
import org.http4s.server.websocket.WebSocketBuilder
import org.http4s.websocket.WebsocketBits.{Binary, Close, Text, WebSocketFrame}
import scalaz.zio.ExitResult

import scala.concurrent.ExecutionContext.Implicits.global


trait WithHttp4sServer {
  this: Http4sContext with WithHttp4sLoggingMiddleware with WithHttp4sHttpRequestContext with WithWebsocketClientContext =>

  class HttpServer[Ctx, ClientId](
                                   protected val muxer: IRTServerMultiplexor[BIO, Ctx]
                                   , protected val codec: IRTClientMultiplexor[BIO]
                                   , protected val contextProvider: AuthMiddleware[CIO, Ctx]
                                   , protected val wsContextProvider: WsContextProvider[Ctx, ClientId]
                                   , protected val listener: WsSessionListener[Ctx, ClientId]
                                 ) {
    protected val dsl: Http4sDsl[CIO] = WithHttp4sServer.this.dsl

    import dsl._

    type WSC = WebsocketClientContext[ClientId, Ctx]
    protected val clients = new ConcurrentHashMap[WsSessionId, WSC]()

    def service: HttpRoutes[CIO] = {
      val svc = AuthedService(handler())
      val aservice: HttpRoutes[CIO] = contextProvider(svc)
      loggingMiddle(aservice)
    }

    protected def handler(): PartialFunction[AuthedRequest[CIO, Ctx], CIO[Response[CIO]]] = {
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


    protected def setupWs(request: AuthedRequest[CIO, Ctx], initialContext: Ctx): CIO[Response[CIO]] = {
      val context = new WebsocketClientContext[ClientId, Ctx](request, initialContext, listener, clients)
      context.start()
      logger.debug(s"${context -> null}: Websocket client connected")

      val handler = handleWsMessage(context) andThen {
        s => Text(s)
      }

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
              .collect(handler)
        }
        val e = q.enqueue
        WebSocketBuilder[CIO].build(d.merge(context.outStream).merge(context.pingStream), e)
      }
    }

    protected def handleWsMessage(context: WSC): PartialFunction[WebSocketFrame, String] = {
      case Text(msg, _) =>
        val ioresponse = makeResponse(context, msg)

        ZIOR.unsafeRunSync(ioresponse) match {
          case ExitResult.Completed(v) =>
            encode(v.asJson)

          case ExitResult.Failed(error, _) =>
            handleWsError(context, List(error), None, "failure")

          case ExitResult.Terminated(causes) =>
            handleWsError(context, causes, None, "termination")
        }

      case v: Binary =>
        handleWsError(context, List.empty, Some(v.toString.take(100) + "..."), "badframe")
    }

    protected def makeResponse(context: WSC, message: String): ZIO[Throwable, RpcPacket] = {
      for {
        parsed <- ZIO.fromEither(parse(message))
        unmarshalled <- ZIO.fromEither(parsed.as[RpcPacket])
        id <- ZIO.syncThrowable(wsContextProvider.toId(context.initialContext, unmarshalled))
        userCtx <- ZIO.syncThrowable(wsContextProvider.toContext(context.initialContext, unmarshalled))
        _ <- ZIO.syncThrowable(context.updateId(id))
        _ <- ZIO.point(logger.debug(s"${context -> null}: $id, $userCtx"))
        response <- respond(context, userCtx, unmarshalled).sandboxWith {
          _.redeem(
            {
              case Left(exception :: otherIssues) =>
                logger.error(s"${context -> null}: WS processing terminated, $message, $exception, $otherIssues")
                ZIO.point(rpc.RpcPacket.rpcFail(unmarshalled.id, exception.getMessage))
              case Left(Nil) =>
                ZIO.terminate(new IllegalStateException())
              case Right(exception) =>
                logger.error(s"${context -> null}: WS processing failed, $message, $exception")
                ZIO.point(rpc.RpcPacket.rpcFail(unmarshalled.id, exception.getMessage))
            }, {
              succ => ZIO.point(succ)
            }
          )
        }
      } yield {
        response
      }
    }

    protected def handleWsError(context: WSC, causes: List[Throwable], data: Option[String], kind: String): String = {
      causes.headOption match {
        case Some(cause) =>
          logger.error(s"${context -> null}: WS Execution failed, $kind, $data, $cause")
          encode(RpcFailureStringResponse(RPCPacketKind.Fail, data.getOrElse(cause.getMessage), kind).asJson)

        case None =>
          logger.error(s"${context -> null}: WS Execution failed, $kind, $data")
          encode(RpcFailureStringResponse(RPCPacketKind.Fail, "?", kind).asJson)
      }
    }

    protected def respond(context: WSC, userContext: Ctx, input: RpcPacket): ZIO[Throwable, RpcPacket] = {
      input match {
        case RpcPacket(RPCPacketKind.RpcRequest, data, Some(id), _, Some(service), Some(method), _) =>
          val methodId = IRTMethodId(IRTServiceId(service), IRTMethodName(method))
          muxer.doInvoke(data, userContext, methodId).flatMap {
            case None =>
              ZIO.fail(new IRTMissingHandlerException(s"${context -> null}: No handler for $methodId", input))
            case Some(resp) =>
              ZIO.point(rpc.RpcPacket.rpcResponse(id, resp))
          }

        case RpcPacket(RPCPacketKind.BuzzResponse, data, _, Some(id), _, _, _) =>
        ???

        case k =>
          ZIO.fail(new UnsupportedOperationException(s"Can't handle $k"))
      }
    }


    protected def run(context: HttpRequestContext[Ctx], body: String, toInvoke: IRTMethodId): CIO[Response[CIO]] = {
      val ioR = for {
        parsed <- ZIO.fromEither(parse(body))
        maybeResult <- muxer.doInvoke(parsed, context.context, toInvoke)
      } yield {
        maybeResult match {
          case Some(value) =>
            dsl.Ok(encode(value))
          case None =>
            logger.warn(s"${context -> null}: No handler for $toInvoke")
            dsl.NotFound()
        }
      }

      ZIOR.unsafeRunSync(ioR) match {
        case ExitResult.Completed(v) =>
          v
        case ExitResult.Failed(error, _) =>
          logger.info(s"${context -> null}: Parsing failure while handling $toInvoke: $error")
          dsl.BadRequest()
        case ExitResult.Terminated(causes) =>
          handleError(context, causes, "termination")
      }
    }

    protected def handleError(context: HttpRequestContext[Ctx], causes: List[Throwable], kind: String): CIO[Response[CIO]] = {
      causes.headOption match {
        case Some(cause: IRTHttpFailureException) =>
          logger.debug(s"${context -> null}: Request rejected, ${context.request}, $cause")
          CIO.pure(Response(status = cause.status))

        case cause =>
          logger.error(s"${context -> null}: Execution failed, $kind, ${context.request}, $cause")
          dsl.InternalServerError()
      }
    }
  }

}
