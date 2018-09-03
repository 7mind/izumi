package com.github.pshirshov.izumi.idealingua.runtime.rpc.http4s

import java.util.concurrent.{ConcurrentHashMap, TimeoutException}

import _root_.io.circe.parser._
import cats.implicits._
import com.github.pshirshov.izumi.idealingua.runtime.rpc
import com.github.pshirshov.izumi.idealingua.runtime.bio.BIO._
import com.github.pshirshov.izumi.idealingua.runtime.rpc.{IRTClientMultiplexor, RPCPacketKind, _}
import io.circe.syntax._
import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.http4s.server.AuthMiddleware
import org.http4s.server.websocket.WebSocketBuilder
import org.http4s.websocket.WebsocketBits.{Binary, Close, Text, WebSocketFrame}
import scalaz.zio.ExitResult

import scala.collection.JavaConverters._
import scala.concurrent.duration._

trait WithHttp4sServer {
  this: Http4sContext with WithHttp4sLoggingMiddleware with WithHttp4sHttpRequestContext with WithWebsocketClientContext =>

  def server[Ctx, ClientId](muxer: IRTServerMultiplexor[BiIO, Ctx]
                            , codec: IRTClientMultiplexor[BiIO]
                            , contextProvider: AuthMiddleware[CatsIO, Ctx]
                            , wsContextProvider: WsContextProvider[Ctx, ClientId]
                            , listener: WsSessionListener[Ctx, ClientId]): HttpServer[Ctx, ClientId] =
    new HttpServer[Ctx, ClientId](muxer, codec, contextProvider, wsContextProvider, listener)

  class HttpServer[Ctx, ClientId](
                                   protected val muxer: IRTServerMultiplexor[BiIO, Ctx]
                                   , protected val codec: IRTClientMultiplexor[BiIO]
                                   , protected val contextProvider: AuthMiddleware[CatsIO, Ctx]
                                   , protected val wsContextProvider: WsContextProvider[Ctx, ClientId]
                                   , protected val listener: WsSessionListener[Ctx, ClientId]
                                 ) {
    protected val dsl: Http4sDsl[CatsIO] = WithHttp4sServer.this.dsl

    import dsl._

    type WSC = WebsocketClientContext[ClientId, Ctx]
    protected val clients = new ConcurrentHashMap[WsSessionId, WSC]()
    protected val timeout: FiniteDuration = 2.seconds
    protected val pollingInterval: FiniteDuration = 50.millis

    def service: HttpRoutes[CatsIO] = {
      val svc = AuthedService(handler())
      val aservice: HttpRoutes[CatsIO] = contextProvider(svc)
      loggingMiddle(aservice)
    }

    def buzzersFor(clientId: ClientId): List[IRTDispatcher[BiIO]] = {
      clients.values().asScala
        .filter(_.id.id.contains(clientId))
        .map {
          sess =>
            new IRTDispatcher[BiIO] {
              override def dispatch(request: IRTMuxRequest): BiIO[Throwable, IRTMuxResponse] = {
                for {
                  session <- BIO.point(sess)
                  json <- codec.encode(request)
                  id <- BIO.sync(session.enqueue(request.method, json))
                  resp <- BIO.bracket0[Throwable, RpcPacketId, IRTMuxResponse](BIO.point(id)) {
                    id =>
                      logger.trace(s"${request.method -> "method"}, ${id -> "id"}: cleaning request state")
                      BIO.sync(sess.requestState.forget(id))
                  } {
                    w =>
                      BIO.point(w).flatMap {
                        id =>
                          sess.requestState.poll(id, pollingInterval, timeout)
                            .flatMap {
                              case Some(value: RawResponse.GoodRawResponse) =>
                                logger.debug(s"${request.method -> "method"}, $id: Have response: $value")
                                codec.decode(value.data, value.method)

                              case Some(value : RawResponse.BadRawResponse) =>
                                logger.debug(s"${request.method -> "method"}, $id: Generic failure response: $value")
                                BIO.terminate(new IRTGenericFailure(s"${request.method -> "method"}, $id: generic failure: $value"))

                              case None =>
                                BIO.terminate(new TimeoutException(s"${request.method -> "method"}, $id: No response in $timeout"))
                            }
                      }
                  }
                } yield {
                  resp
                }
              }
            }
        }
        .toList
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
      val context = new WebsocketClientContext[ClientId, Ctx](request, initialContext, listener, clients)
      context.start()
      logger.debug(s"${context -> null}: Websocket client connected")

      val handler = handleWsMessage(context)

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
              .collect({ case Some(v) => Text(v) })
        }
        val e = q.enqueue
        WebSocketBuilder[CatsIO].build(d.merge(context.outStream).merge(context.pingStream), e)
      }
    }

    protected def handleWsMessage(context: WSC): PartialFunction[WebSocketFrame, Option[String]] = {
      case Text(msg, _) =>
        val ioresponse = makeResponse(context, msg)

        BIORunner.unsafeRunSync0(ioresponse) match {
          case ExitResult.Completed(v) =>
            v.map(_.asJson).map(printer.pretty)

          case ExitResult.Failed(error, _) =>
            Some(handleWsError(context, List(error), None, "failure"))

          case ExitResult.Terminated(causes) =>
            Some(handleWsError(context, causes, None, "termination"))
        }

      case v: Binary =>
        Some(handleWsError(context, List.empty, Some(v.toString.take(100) + "..."), "badframe"))
    }

    protected def makeResponse(context: WSC, message: String): BiIO[Throwable, Option[RpcPacket]] = {
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

    protected def handleWsError(context: WSC, causes: List[Throwable], data: Option[String], kind: String): String = {
      causes.headOption match {
        case Some(cause) =>
          logger.error(s"${context -> null}: WS Execution failed, $kind, $data, $cause")
          printer.pretty(rpc.RpcPacket.rpcCritical(data.getOrElse(cause.getMessage), kind).asJson)

        case None =>
          logger.error(s"${context -> null}: WS Execution failed, $kind, $data")
          printer.pretty(rpc.RpcPacket.rpcCritical("?", kind).asJson)
      }
    }

    protected def respond(context: WSC, userContext: Ctx, input: RpcPacket): BiIO[Throwable, Option[RpcPacket]] = {
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
        maybeResult match {
          case Some(value) =>
            dsl.Ok(printer.pretty(value))
          case None =>
            logger.warn(s"${context -> null}: No service handler for $method")
            dsl.NotFound()
        }
      }

      BIORunner.unsafeRunSync0(ioR) match {
        case ExitResult.Completed(v) =>
          v
        case ExitResult.Failed(error, _) =>
          logger.info(s"${context -> null}: Parsing failure while handling $method: $error")
          dsl.BadRequest()
        case ExitResult.Terminated(causes) =>
          handleError(context, method, causes, "termination")
      }
    }

    protected def handleError(context: HttpRequestContext[Ctx], method: IRTMethodId, causes: List[Throwable], kind: String): CatsIO[Response[CatsIO]] = {
      causes.headOption match {
        case Some(cause: IRTHttpFailureException) =>
          logger.debug(s"${context -> null}: Request rejected, $method, ${context.request}, $cause")
          CIO.pure(Response(status = cause.status))

        case cause =>
          logger.error(s"${context -> null}: Execution failed, $kind, $method, ${context.request}, $cause")
          dsl.InternalServerError()
      }
    }
  }

}
