package com.github.pshirshov.izumi.idealingua.runtime.rpc.http4s

import java.net.URI
import java.util.concurrent.TimeoutException

import com.github.pshirshov.izumi.functional.bio.BIO._
import com.github.pshirshov.izumi.idealingua.runtime.rpc
import com.github.pshirshov.izumi.idealingua.runtime.rpc._
import io.circe.parser.parse
import io.circe.syntax._
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake

case class PacketInfo(method: IRTMethodId, packetId: RpcPacketId)

trait WsClientContextProvider[Ctx] {
  def toContext(packet: RpcPacket): Ctx
}

/**
  * TODO: this is a naive client implementation, good for testing purposes but not mature enough for production usage
  */
class ClientWsDispatcher[C <: Http4sContext]
(
  val c: C#IMPL[C],
  protected val baseUri: URI,
  protected val codec: IRTClientMultiplexor[C#BiIO],
  protected val buzzerMuxer: IRTServerMultiplexor[C#BiIO, C#ClientContext],
  protected val wsClientContextProvider: WsClientContextProvider[C#ClientContext],
)
  extends IRTDispatcher[C#BiIO] with AutoCloseable {

  import c._

  val requestState = new RequestState[BiIO]()

  protected val wsClient: WebSocketClient = new WebSocketClient(baseUri) {
    override def onOpen(handshakedata: ServerHandshake): Unit = {}

    override def onMessage(message: String): Unit = {
      logger.error(s"Incoming WS message: $message")

      val result = for {
        parsed <- BIO.fromEither(parse(message))
        _ <- BIO.sync(logger.info(s"parsed: $parsed"))
        decoded <- BIO.fromEither(parsed.as[RpcPacket])
        v <- routeResponse(decoded)
      } yield {
        v
      }

      BIORunner.unsafeRunAsyncAsEither(result) {
        case scala.util.Success(Right(PacketInfo(packetId, method))) =>
          logger.debug(s"Processed incoming packet $method: $packetId")

        case scala.util.Success(Left(error)) =>
          logger.error(s"Failed to process request: $error")

        case scala.util.Failure(cause) =>
          logger.error(s"Failed to process request, termination: $cause")

      }
    }

    override def onClose(code: Int, reason: String, remote: Boolean): Unit = {
      logger.debug(s"WS connection closed: $code, $reason, $remote")
    }

    override def onError(exception: Exception): Unit = {
      logger.debug(s"WS connection errored: $exception")
    }
  }

  protected def routeResponse(decoded: RpcPacket): BiIO[Throwable, PacketInfo] = {
    decoded match {
      case RpcPacket(RPCPacketKind.RpcResponse, data, _, ref, _, _, _) =>
        requestState.handleResponse(ref, data)

      case p@RpcPacket(RPCPacketKind.BuzzRequest, data, Some(id), _, Some(service), Some(method), _) =>
        val methodId = IRTMethodId(IRTServiceId(service), IRTMethodName(method))
        val packetInfo = PacketInfo(methodId, id)

        val responsePkt = for {
          maybeResponse <- buzzerMuxer.doInvoke(data, wsClientContextProvider.toContext(p), methodId)
          maybePacket <- BIO.now(maybeResponse.map(r => RpcPacket.buzzerResponse(id, r)))
        } yield {
          maybePacket
        }

        for {
          maybePacket <- responsePkt.sandboxWith {
            _.redeem(
              {
                case Left(exception :: otherIssues) =>
                  logger.error(s"${packetInfo -> null}: WS processing terminated, $exception, $otherIssues")
                  BIO.point(Some(rpc.RpcPacket.buzzerFail(Some(id), exception.getMessage)))
                case Left(Nil) =>
                  BIO.terminate(new IllegalStateException())
                case Right(exception) =>
                  logger.error(s"${packetInfo -> null}: WS processing failed, $exception")
                  BIO.point(Some(rpc.RpcPacket.buzzerFail(Some(id), exception.getMessage)))
              }, {
                succ => BIO.point(succ)
              }
            )
          }
          maybeEncoded <- BIO.syncThrowable(maybePacket.map(r => printer.pretty(r.asJson)))
          _ <- BIO.point {
            maybeEncoded match {
              case Some(response) =>
                logger.debug(s"${method -> "method"}, ${id -> "id"}: Prepared buzzer $response")
                wsClient.send(response)
              case None =>
            }
          }
        } yield {
          packetInfo
        }

      case RpcPacket(RPCPacketKind.RpcFail, data, _, Some(ref), _, _, _) =>
        requestState.respond(ref, RawResponse.BadRawResponse())
        BIO.fail(new IRTGenericFailure(s"RPC failure for $ref: $data"))

      case RpcPacket(RPCPacketKind.RpcFail, data, _, None, _, _, _) =>
        BIO.fail(new IRTGenericFailure(s"Missing ref in RPC failure: $data"))

      case RpcPacket(RPCPacketKind.Fail, data, _, _, _, _, _) =>
        BIO.fail(new IRTGenericFailure(s"Critical RPC failure: $data"))

      case o =>
        BIO.fail(new IRTMissingHandlerException(s"No buzzer client handler for $o", o))
    }
  }

  wsClient.connect()
  while (!wsClient.isOpen) {
    Thread.`yield`()
  }

  override def close(): Unit = {
    wsClient.closeBlocking()
  }

  import scala.concurrent.duration._

  protected val timeout: FiniteDuration = 2.seconds
  protected val pollingInterval: FiniteDuration = 50.millis

  def dispatch(request: IRTMuxRequest): BiIO[Throwable, IRTMuxResponse] = {
    logger.trace(s"${request.method -> "method"}: Going to perform $request")

    codec
      .encode(request)
      .flatMap {
        encoded =>
          val wrapped = BIO.point(RpcPacket.rpcRequest(request.method, encoded))


          BIO.bracket[Throwable, RpcPacket, IRTMuxResponse](wrapped) {
            id =>
              logger.trace(s"${request.method -> "method"}, ${id -> "id"}: cleaning request state")
              BIO.sync(requestState.forget(id.id.get))
          } {
            w =>
              val pid = w.id.get // guaranteed to be present

              BIO.syncThrowable {
                val out = printer.pretty(transformRequest(w).asJson)
                logger.debug(s"${request.method -> "method"}, ${pid -> "id"}: Prepared request $encoded")
                requestState.request(pid, request.method)
                wsClient.send(out)
                pid
              }
                .flatMap {
                  id =>
                    requestState.poll(id, pollingInterval, timeout)
                      .flatMap {
                        case Some(value: RawResponse.GoodRawResponse) =>
                          logger.debug(s"${request.method -> "method"}, $id: Have response: $value")
                          codec.decode(value.data, value.method)

                        case Some(value: RawResponse.BadRawResponse) =>
                          logger.debug(s"${request.method -> "method"}, $id: Have response: $value")
                          BIO.terminate(new IRTGenericFailure(s"${request.method -> "method"}, $id: generic failure: $value"))

                        case None =>
                          BIO.terminate(new TimeoutException(s"${request.method -> "method"}, $id: No response in $timeout"))
                      }
                }
          }
      }
  }

  protected def transformRequest(request: RpcPacket): RpcPacket = request
}
