package com.github.pshirshov.izumi.idealingua.runtime.rpc.http4s

import java.net.URI
import java.util.concurrent.TimeoutException

import com.github.pshirshov.izumi.idealingua.runtime.rpc._
import io.circe.parser.parse
import io.circe.syntax._
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import scalaz.zio.{ExitResult, IO}


trait WithHttp4sWsClient {
  this: Http4sContext =>

  class ClientWsDispatcher(baseUri: URI, codec: IRTClientMultiplexor[BIO])
    extends IRTDispatcher with IRTResultZio with AutoCloseable {

    val requestState = new RequestState()

    protected val wsClient: WebSocketClient = new WebSocketClient(baseUri) {
      override def onOpen(handshakedata: ServerHandshake): Unit = {}

      override def onMessage(message: String): Unit = {
        logger.error(s"Incoming WS message: $message")

        val result = for {
          parsed <- IO.fromEither(parse(message))
          _ <- IO.sync(logger.info(s"parsed: $parsed"))
          decoded <- IO.fromEither(parsed.as[RpcPacket])
          v <- requestState.handleResponse(decoded.ref, decoded.data)
        } yield {
          v
        }

        ZIOR.unsafeRunSync(result) match {
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

    def dispatch(request: IRTMuxRequest): ZIO[Throwable, IRTMuxResponse] = {
      logger.trace(s"${request.method -> "method"}: Going to perform $request")

      codec
        .encode(request)
        .flatMap {
          encoded =>
            val wrapped = IO.point(RpcPacket.rpcRequest(request.method, encoded))
            IO.bracket0[Throwable, RpcPacket, IRTMuxResponse](wrapped) {
              (id, _) =>
                logger.trace(s"${request.method -> "method"}, ${id -> "id"}: cleaning request state")
                IO.sync(requestState.forget(id.id.get))
            } {
              w =>
                val pid = w.id.get // guaranteed to be present

                IO.syncThrowable {
                  val out = encode(transformRequest(w).asJson)
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
                            IO.terminate(new TimeoutException(s"${request.method -> "method"}, $id: No response in $timeout"))
                        }
                  }
            }
        }
    }

    protected def transformRequest(request: RpcPacket): RpcPacket = request
  }

}
