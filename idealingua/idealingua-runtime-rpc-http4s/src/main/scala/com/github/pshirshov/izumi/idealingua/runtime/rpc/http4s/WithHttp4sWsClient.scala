package com.github.pshirshov.izumi.idealingua.runtime.rpc.http4s

import java.net.URI
import java.util.concurrent.TimeoutException

import com.github.pshirshov.izumi.idealingua.runtime.bio.BIO._
import com.github.pshirshov.izumi.idealingua.runtime.rpc._
import io.circe.parser.parse
import io.circe.syntax._
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import scalaz.zio.ExitResult

case class PacketInfo(method: IRTMethodId, packetId: RpcPacketId)

trait WsClientContextProvider[Ctx] {
  def toContext(packet: RpcPacket): Ctx
}

trait WithHttp4sWsClient {
  self: Http4sContext =>

  def wsClient[Ctx](
                     baseUri: URI
                     , codec: IRTClientMultiplexor[BiIO]
                     , buzzerMuxer: IRTServerMultiplexor[BiIO, Ctx]
                     , wsClientContextProvider: WsClientContextProvider[Ctx]
                   ): ClientWsDispatcher[Ctx] = new ClientWsDispatcher[Ctx](baseUri, codec, buzzerMuxer, wsClientContextProvider)

  /**
    * TODO: this is a naive client implementation, good for testing purposes but not mature enough for production usage
    */
  class ClientWsDispatcher[Ctx](
                                 protected val baseUri: URI
                                 , protected val codec: IRTClientMultiplexor[BiIO]
                                 , protected val buzzerMuxer: IRTServerMultiplexor[BiIO, Ctx]
                                 , protected val wsClientContextProvider: WsClientContextProvider[Ctx]
                               )
    extends IRTDispatcher[BiIO] with AutoCloseable {

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

        BIORunner.unsafeRunSync0(result) match {
          case ExitResult.Completed(PacketInfo(packetId, method)) =>
            logger.debug(s"Processed incoming packet $method: $packetId")

          case ExitResult.Failed(error, _) =>
            logger.error(s"Failed to process request: $error")


          case ExitResult.Terminated(causes) =>
            logger.error(s"Failed to process request, termination: $causes")
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
          for {
            maybeResponse <- buzzerMuxer.doInvoke(data, wsClientContextProvider.toContext(p), methodId)
            maybePacket <- BIO.point(maybeResponse.map(r => RpcPacket.buzzerResponse(id, r)))
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

        case RpcPacket(RPCPacketKind.RpcFail, data, _, ref, _, _, _) =>
          ref match {
            case Some(value) =>
              if (requestState.methodOf(value).nonEmpty) {
                requestState.forget(value) // TODO: we should support exception passing in RequestState
              }
            case None =>
          }

          ref.foreach(requestState.forget)
          BIO.fail(new IRTGenericFailure(s"RPC failure for $ref: $data"))

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


            BIO.bracket0[Throwable, RpcPacket, IRTMuxResponse](wrapped) {
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
                          case Some(value) =>
                            logger.debug(s"${request.method -> "method"}, $id: Have response: $value")
                            codec.decode(value.data, value.method)

                          case None =>
                            BIO.terminate(new TimeoutException(s"${request.method -> "method"}, $id: No response in $timeout"))
                        }
                  }
            }
        }
    }

    protected def transformRequest(request: RpcPacket): RpcPacket = request
  }

}
