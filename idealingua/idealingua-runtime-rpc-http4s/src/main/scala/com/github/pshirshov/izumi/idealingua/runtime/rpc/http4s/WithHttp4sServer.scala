package com.github.pshirshov.izumi.idealingua.runtime.rpc.http4s

import _root_.io.circe.parser._
import com.github.pshirshov.izumi.idealingua.runtime.rpc.{RPCPacketKind, _}
import fs2.async
import io.circe.syntax._
import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.http4s.server.AuthMiddleware
import org.http4s.server.websocket.WebSocketBuilder
import org.http4s.websocket.WebsocketBits.{Text, WebSocketFrame}
import scalaz.zio.ExitResult

import scala.concurrent.ExecutionContext.Implicits.global


trait WithHttp4sServer {
  this: Http4sContext with WithHttp4sLoggingMiddleware =>

  class HttpServer[Ctx](
                         protected val muxer: IRTServerMultiplexor[BIO, Ctx]
                         , protected val contextProvider: AuthMiddleware[CIO, Ctx]
                       ) {
    protected val dsl: Http4sDsl[CIO] = WithHttp4sServer.this.dsl

    import dsl._

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
        run(request, ctx, body = "{}", methodId)

      case request@POST -> Root / service / method as ctx =>
        val methodId = IRTMethodId(IRTServiceId(service), IRTMethodName(method))
        request.req.decode[String] {
          body =>
            run(request, ctx, body, methodId)
        }
    }

    protected def setupWs(request: AuthedRequest[CIO, Ctx], context: Ctx): CIO[Response[CIO]] = {
      val queue = async.unboundedQueue[CIO, WebSocketFrame]
      queue.flatMap { q =>
        val d = q.dequeue.through {
          _.map(handleWsMessage(request, context, _))
        }
        val e = q.enqueue
        WebSocketBuilder[CIO].build(d, e)
      }
    }

    protected def handleWsMessage(request: AuthedRequest[CIO, Ctx], context: Ctx, input: WebSocketFrame): WebSocketFrame = {
      import io.circe.syntax._

      val output: String = input match {
        case Text(msg, _) =>
          val ioresponse = makeResponse(request, context, msg)

          ZIOR.unsafeRunSync(ioresponse) match {
            case ExitResult.Completed(v) =>
              v.asJson.noSpaces

            case ExitResult.Failed(error, _) =>
              handleWsError(request, List(error), None, "failure")

            case ExitResult.Terminated(causes) =>
              handleWsError(request, List.empty, None, "termination")

          }

        case v =>
          handleWsError(request, List.empty, Some(v.toString.toString.take(100) + "..."), "badframe")
      }

      Text(output)
    }

    protected def handleWsError(request: AuthedRequest[CIO, Ctx], causes: List[Throwable], data: Option[String], kind: String): String = {
      causes.headOption match {
        case Some(cause) =>
          logger.error(s"WS Execution failed, $kind, $request, $cause")
          RpcStringResponse(RPCPacketKind.Fail, data.getOrElse(cause.getMessage), kind).asJson.noSpaces

        case None =>
          logger.error(s"WS Execution failed, $kind, $request")
          RpcStringResponse(RPCPacketKind.Fail, "?", kind).asJson.noSpaces
      }
    }

    protected def makeResponse(request: AuthedRequest[CIO, Ctx], context: Ctx, message: String): ZIO[Throwable, RpcResponse] = {
      for {
        parsed <- ZIO.fromEither(parse(message))
        unmarshalled <- ZIO.fromEither(parsed.as[RpcRequest])
        response <- respond(request, context, unmarshalled)
          .catchAll {
            exception =>
              logger.error(s"WS processing failed, $message, $exception")
              ZIO.point(RpcResponse(RPCPacketKind.RpcFail, unmarshalled.id, exception.getMessage.asJson))
          }
      } yield {
        response
      }
    }

    protected def respond(request: AuthedRequest[CIO, Ctx], context: Ctx, input: RpcRequest): ZIO[Throwable, RpcResponse] = {
      input.kind match {
        case RPCPacketKind.RpcRequest =>
          val methodId = IRTMethodId(IRTServiceId(input.service), IRTMethodName(input.method))
          muxer.doInvoke(input.data, context, methodId).flatMap {
            case None =>
              ZIO.fail(new IRTMissingHandlerException(s"No handler for $methodId", input))
            case Some(resp) =>
              ZIO.point(RpcResponse(RPCPacketKind.RpcResponse, input.id, resp))
          }

        case k =>
          ZIO.fail(new UnsupportedOperationException(s"Can't handle $k. At the moment WS transport supports client2server requests only."))
      }
    }

    protected def run(request: AuthedRequest[CIO, Ctx], context: Ctx, body: String, toInvoke: IRTMethodId): CIO[Response[CIO]] = {
      val ioR = for {
        parsed <- ZIO.fromEither(parse(body))
        maybeResult <- muxer.doInvoke(parsed, context, toInvoke)
      } yield {
        maybeResult match {
          case Some(value) =>
            dsl.Ok(value.noSpaces)
          case None =>
            logger.trace(s"No handler for $request")
            dsl.NotFound()
        }

      }


      ZIOR.unsafeRunSync(ioR) match {
        case ExitResult.Completed(v) =>
          v
        case ExitResult.Failed(error, _) =>
          logger.trace(s"Parsing failure while handling $request: $error")
          dsl.BadRequest()
        case ExitResult.Terminated(causes) =>
          handleError(request, causes, "termination")
      }
    }

    protected def handleError(request: AuthedRequest[CIO, Ctx], causes: List[Throwable], kind: String): CIO[Response[CIO]] = {
      causes.headOption match {
        case Some(cause: IRTHttpFailureException) =>
          logger.debug(s"Request rejected, $request, $cause")
          CIO.pure(Response(status = cause.status))

        case cause =>
          logger.error(s"Execution failed, $kind, $request, $cause")
          dsl.InternalServerError()
      }
    }
  }

}
