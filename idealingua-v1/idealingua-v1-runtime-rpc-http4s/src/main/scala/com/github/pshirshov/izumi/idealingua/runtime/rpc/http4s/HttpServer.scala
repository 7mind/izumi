package com.github.pshirshov.izumi.idealingua.runtime.rpc.http4s

import java.time.ZonedDateTime
import java.util.concurrent.RejectedExecutionException

import _root_.io.circe.parser._
import com.github.pshirshov.izumi.functional.bio.BIO._
import com.github.pshirshov.izumi.functional.bio.BIOExit
import com.github.pshirshov.izumi.functional.bio.BIOExit.{Error, Success, Termination}
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks._
import com.github.pshirshov.izumi.fundamentals.platform.time.IzTime
import com.github.pshirshov.izumi.idealingua.runtime.rpc
import com.github.pshirshov.izumi.idealingua.runtime.rpc.{IRTClientMultiplexor, RPCPacketKind, _}
import com.github.pshirshov.izumi.logstage.api.IzLogger
import io.circe
import io.circe.syntax._
import io.circe.{Json, Printer}
import org.http4s._
import org.http4s.server.AuthMiddleware
import org.http4s.server.websocket.WebSocketBuilder
import org.http4s.websocket.WebSocketFrame
import org.http4s.websocket.WebSocketFrame.{Binary, Close, Pong, Text}

class HttpServer[C <: Http4sContext]
(
  val c: C#IMPL[C]
, val muxer: IRTServerMultiplexor[C#BiIO, C#RequestContext, C#MethodContext]
, val codec: IRTClientMultiplexor[C#BiIO]
, val contextProvider: AuthMiddleware[C#MonoIO, C#RequestContext]
, val wsContextProvider: WsContextProvider[C#BiIO, C#RequestContext, C#ClientId]
, val wsSessionStorage: WsSessionsStorage[C#BiIO, C#ClientId, C#RequestContext]
, val listeners: Seq[WsSessionListener[C#ClientId]]
, logger: IzLogger
, printer: Printer
) {

  import c._
  import c.dsl._

  protected def loggingMiddle(service: HttpRoutes[MonoIO]): HttpRoutes[MonoIO] = cats.data.Kleisli {
    req: Request[MonoIO] =>
      logger.trace(s"${req.method.name -> "method"} ${req.pathInfo -> "path"}: initiated")

      try {
        service(req).map {
          case Status.Successful(resp) =>
            logger.debug(s"${req.method.name -> "method"} ${req.pathInfo -> "path"}: success, ${resp.status.code -> "code"} ${resp.status.reason -> "reason"}")
            resp
          case resp if resp.attributes.lookup(org.http4s.server.websocket.websocketKey[MonoIO]).isDefined =>
            logger.debug(s"${req.method.name -> "method"} ${req.pathInfo -> "path"}: websocket request")
            resp
          case resp =>
            logger.info(s"${req.method.name -> "method"} ${req.pathInfo -> "uri"}: rejection, ${resp.status.code -> "code"} ${resp.status.reason -> "reason"}")
            resp
        }
      } catch {
        case cause: Throwable =>
          logger.error(s"${req.method.name -> "method"} ${req.pathInfo -> "path"}: failure, $cause")
          throw cause
      }
  }

  def service: HttpRoutes[MonoIO] = {
    val svc = AuthedService(handler())
    val aservice: HttpRoutes[MonoIO] = contextProvider(svc)
    loggingMiddle(aservice)
  }

  protected def handler(): PartialFunction[AuthedRequest[MonoIO, RequestContext], MonoIO[Response[MonoIO]]] = {
    case request@GET -> Root / "ws" as ctx =>
      val result = setupWs(request, ctx)
      result

    case request@GET -> Root / service / method as ctx =>
      val methodId = IRTMethodId(IRTServiceId(service), IRTMethodName(method))
      run(new HttpRequestContext(request, ctx), body = "{}", methodId)

    case request@POST -> Root / service / method as ctx =>
      val methodId = IRTMethodId(IRTServiceId(service), IRTMethodName(method))
      request.req.decode[String] {
        body =>
          run(new HttpRequestContext(request, ctx), body, methodId)
      }
  }

  protected def handleWsClose(context: WebsocketClientContext[C#BiIO, C#ClientId, C#RequestContext]): Unit = {
    logger.debug(s"${context -> null}: Websocket client disconnected")
    context.finish()
  }

  protected def onWsOpened(): Unit = {
  }

  protected def onWsUpdate(maybeNewId: Option[C#ClientId], old: WsClientId[ClientId]): Unit = {
    (maybeNewId, old).forget
  }

  protected def onWsClosed(): Unit = {
  }

  protected def setupWs(request: AuthedRequest[MonoIO, RequestContext], initialContext: RequestContext): MonoIO[Response[MonoIO]] = {
    val context = new WebsocketClientContextImpl[C](c, request, initialContext, listeners, wsSessionStorage, logger) {

      override def onWsSessionOpened(): Unit = {
          onWsOpened()
          super.onWsSessionOpened()
      }

      override def onWsClientIdUpdate(maybeNewId: Option[C#ClientId], oldId: WsClientId[C#ClientId]): Unit = {
        onWsUpdate(maybeNewId, oldId)
        super.onWsClientIdUpdate(maybeNewId, oldId)
      }

      override def onWsSessionClosed(): Unit = {
        onWsClosed()
        super.onWsSessionClosed()
      }
    }
    context.start()
    logger.debug(s"${context -> null}: Websocket client connected")

    context.queue.flatMap[Throwable, Response[MonoIO]] { q =>
      val dequeueStream = q.dequeue.through {
        stream =>
          stream
            .evalMap(handleWsMessage(context))
            .collect({ case Some(v) => WebSocketFrame.Text(v) })
      }
      val enqueueSink = q.enqueue
      WebSocketBuilder[MonoIO].build(
        send = dequeueStream.merge(context.outStream).merge(context.pingStream)
      , receive = enqueueSink
      , onClose = BIO.syncThrowable(handleWsClose(context))
      )
    }
  }

  protected def handleWsMessage(context: WebsocketClientContextImpl[C], requestTime: ZonedDateTime = IzTime.utcNow): WebSocketFrame => MonoIO[Option[String]] = {
    case Text(msg, _) =>
      makeResponse(context, msg)
        .sandbox
        .redeemPure(identity, BIOExit.Success(_))
        .map(handleResult(context, _))

    case Close(_) =>
      BIO.pure(None)

    case v: Binary =>
      BIO.pure(Some(handleWsError(context, List.empty, Some(v.toString.take(100) + "..."), "badframe")))

    case _: Pong =>
      onHeartbeat(requestTime).map(_ => None)

    case unknownMessage =>
      logger.error(s"Cannot handle unknown websocket message $unknownMessage")
      BIO.pure(None)
  }

  def onHeartbeat(requestTime: ZonedDateTime): C#MonoIO[Unit] = {
    requestTime.discard()
    BIO.unit
  }

  protected def handleResult(context: WebsocketClientContextImpl[C], result: BIOExit[Throwable, Option[RpcPacket]]): Option[String] = {
    result match {
      case Success(v) =>
        v.map(_.asJson).map(printer.pretty)

      case Error(error, _) =>
        Some(handleWsError(context, List(error), None, "failure"))

      case Termination(cause, _, _) =>
        Some(handleWsError(context, List(cause), None, "termination"))
    }
  }

  protected def makeResponse(context: WebsocketClientContextImpl[C], message: String): BiIO[Throwable, Option[RpcPacket]] = {
    for {
      parsed <- BIO.fromEither(parse(message))
      unmarshalled <- BIO.fromEither(parsed.as[RpcPacket])
      id <- BIO.syncThrowable(wsContextProvider.toId(context.initialContext, context.id, unmarshalled))
      _ <- BIO.syncThrowable(context.updateId(id))
      response <- respond(context, unmarshalled).sandbox.catchAll {
        case BIOExit.Termination(exception, allExceptions, trace) =>
          logger.error(s"${context -> null}: WS processing terminated, $message, $exception, $allExceptions, $trace")
          BIO.pure(Some(rpc.RpcPacket.rpcFail(unmarshalled.id, exception.getMessage)))
        case BIOExit.Error(exception, trace) =>
          logger.error(s"${context -> null}: WS processing failed, $message, $exception $trace")
          BIO.pure(Some(rpc.RpcPacket.rpcFail(unmarshalled.id, exception.getMessage)))
      }
    } yield response
  }

  protected def respond(context: WebsocketClientContextImpl[C], input: RpcPacket): BiIO[Throwable, Option[RpcPacket]] = {
    input match {
      case RpcPacket(RPCPacketKind.RpcRequest, None, _, _, _, _, _) =>
        val (newId, response) = wsContextProvider.handleEmptyBodyPacket(context.id, context.initialContext, input)
        BIO.syncThrowable(context.updateId(newId)) *> response

      case RpcPacket(RPCPacketKind.RpcRequest, Some(data), Some(id), _, Some(service), Some(method), _) =>
        val methodId = IRTMethodId(IRTServiceId(service), IRTMethodName(method))
        for {
          userCtx <- BIO.syncThrowable(wsContextProvider.toContext(context.id, context.initialContext, input))
          _ <- BIO.sync(logger.debug(s"${context -> null}: $id, $userCtx"))
          result <- muxer.doInvoke(data, userCtx, methodId)
          packet <- result match {
            case None =>
              BIO.fail(new IRTMissingHandlerException(s"${context -> null}: No rpc handler for $methodId", input))
            case Some(resp) =>
              BIO.pure(rpc.RpcPacket.rpcResponse(id, resp))
          }
        } yield {
          Some(packet)
        }

      case RpcPacket(RPCPacketKind.BuzzResponse, Some(data), _, id, _, _, _) =>
        context.requestState.handleResponse(id, data).as(None)

      case RpcPacket(RPCPacketKind.BuzzFailure, Some(data), _, Some(id), _, _, _) =>
        context.requestState.respond(id, RawResponse.BadRawResponse())
        BIO.fail(new IRTGenericFailure(s"Buzzer has returned failure: $data"))

      case k =>
        BIO.fail(new IRTMissingHandlerException(s"Can't handle $k", k))
    }
  }

  protected def handleWsError(context: WebsocketClientContextImpl[C], causes: List[Throwable], data: Option[String], kind: String): String = {
    causes.headOption match {
      case Some(cause) =>
        logger.error(s"${context -> null}: WS Execution failed, $kind, $data, $cause")
        printer.pretty(rpc.RpcPacket.rpcCritical(data.getOrElse(cause.getMessage), kind).asJson)

      case None =>
        logger.error(s"${context -> null}: WS Execution failed, $kind, $data")
        printer.pretty(rpc.RpcPacket.rpcCritical("?", kind).asJson)
    }
  }


  protected def run(context: HttpRequestContext[MonoIO, RequestContext], body: String, method: IRTMethodId): MonoIO[Response[MonoIO]] = {
    val ioR = for {
      parsed <- BIO.fromEither(parse(body))
      maybeResult <- muxer.doInvoke(parsed, context.context, method)
    } yield {
      maybeResult
    }

    ioR.sandbox
      .redeemPure(identity, BIOExit.Success(_))
      .flatMap(handleResult(context, method, _))
  }

  private def handleResult(context: HttpRequestContext[MonoIO, RequestContext], method: IRTMethodId, result: BIOExit[Throwable, Option[Json]]): MonoIO[Response[MonoIO]] = {
    result match {
      case Success(v) =>
        v match {
          case Some(value) =>
            dsl.Ok(printer.pretty(value))
          case None =>
            logger.warn(s"${context -> null}: No service handler for $method")
            dsl.NotFound()
        }

      case Error(error: circe.Error, trace) =>
        logger.info(s"${context -> null}: Parsing failure while handling $method: $error $trace")
        dsl.BadRequest()

      case Error(error: IRTDecodingException, trace) =>
        logger.info(s"${context -> null}: Parsing failure while handling $method: $error $trace")
        dsl.BadRequest()

      case Error(error: IRTLimitReachedException, trace) =>
        logger.debug(s"${context -> null}: Request failed because of request limit reached $method: $error $trace")
        dsl.TooManyRequests()

      case Error(error: IRTUnathorizedRequestContextException, trace) =>
        logger.debug(s"${context -> null}: Request failed because of unexpected request context reached $method: $error $trace")
        // workaarount because implicits conflict
        dsl.Forbidden().map(_.copy(status = dsl.Unauthorized))

      case Error(error, trace) =>
        logger.info(s"${context -> null}: Unexpected failure while handling $method: $error $trace")
        dsl.InternalServerError()

      case Termination(_, (cause: IRTHttpFailureException) :: _, trace) =>
        logger.debug(s"${context -> null}: Request rejected, $method, ${context.request}, $cause, $trace")
        BIO.pure(Response(status = cause.status))

      case Termination(_, (cause: RejectedExecutionException) :: _, trace) =>
        logger.warn(s"${context -> null}: Not enough capacity to handle $method: $cause $trace")
        dsl.TooManyRequests()

      case Termination(cause, _, trace) =>
        logger.error(s"${context -> null}: Execution failed, termination, $method, ${context.request}, $cause, $trace")
        dsl.InternalServerError()
    }
  }

}
