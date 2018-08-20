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

trait WithHttp4sWsClient {
  self: Http4sContext =>

  def wsClient(baseUri: URI, codec: IRTClientMultiplexor[BiIO]): ClientWsDispatcher = new ClientWsDispatcher(baseUri, codec)

  class ClientWsDispatcher(baseUri: URI, codec: IRTClientMultiplexor[BiIO])
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
          v <- requestState.handleResponse(decoded.ref, decoded.data)
        } yield {
          v
        }

        BIORunner.unsafeRunSync0(result) match {
          case ExitResult.Completed((packetId, method)) =>
            logger.debug(s"Have reponse for method $method: $packetId")

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
