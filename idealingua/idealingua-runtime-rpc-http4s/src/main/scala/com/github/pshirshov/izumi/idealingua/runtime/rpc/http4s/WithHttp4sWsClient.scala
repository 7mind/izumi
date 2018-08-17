package com.github.pshirshov.izumi.idealingua.runtime.rpc.http4s

import java.net.URI
import java.util.concurrent.{ConcurrentHashMap, TimeoutException}

import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks
import com.github.pshirshov.izumi.idealingua.runtime.rpc.{RPCPacketKind, _}
import io.circe.parser.parse
import io.circe.syntax._
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import scalaz.zio.{ExitResult, IO}

trait WithHttp4sWsClient {
  this: Http4sContext =>

  class ClientWsDispatcher(baseUri: URI, codec: IRTClientMultiplexor[BIO])
    extends IRTDispatcher with IRTResultZio with AutoCloseable {

    // TODO: no stale item cleanups
    protected val requests: ConcurrentHashMap[RpcPacketId, IRTMethodId] = new ConcurrentHashMap[RpcPacketId, IRTMethodId]()
    protected val responses: ConcurrentHashMap[RpcPacketId, IRTMuxResponse] = new ConcurrentHashMap[RpcPacketId, IRTMuxResponse]()

    protected val wsClient: WebSocketClient = new WebSocketClient(baseUri) {
      override def onOpen(handshakedata: ServerHandshake): Unit = {}

      override def onMessage(message: String): Unit = {
        logger.error(s"Incoming WS message: $message")

        val result = for {
          parsed <- IO.fromEither(parse(message))
          _ <- IO.sync(logger.info(s"parsed: $parsed"))
          decoded <- IO.fromEither(parsed.as[RpcResponse])
          method <- Option(requests.get(decoded.ref)) match {
            case Some(id) =>
              requests.remove(decoded.ref)
              IO.point(id)
            case None => IO.terminate(new IRTMissingHandlerException(s"No handler for ${decoded.ref}", decoded))
          }
          product <- codec.decode(decoded.data, method)
        } yield {
          (product, decoded)
        }

        ZIOR.unsafeRunSync(result) match {
          case ExitResult.Completed(v) =>
            Quirks.discard(responses.put(v._2.ref, v._1))

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

    def dispatch(request: IRTMuxRequest): ZIO[Throwable, IRTMuxResponse] = {
      logger.trace(s"${request.method -> "method"}: Going to perform $request")

      codec
        .encode(request)
        .flatMap {
          encoded =>
            val wrapped = IO.point {
              RpcRequest(
                RPCPacketKind.RpcRequest,
                request.method.service.value,
                request.method.methodId.value,
                RpcPacketId.random(),
                encoded,
                Map.empty,
              )
            }


            IO.bracket0[Throwable, RpcRequest, IRTMuxResponse](wrapped) {
              (id, _) =>
                logger.trace(s"${request.method -> "method"}, ${id -> "id"}: cleaning request state")
                IO.sync(Quirks.discard(requests.remove(id.id), responses.remove(id.id)))
            } {
              w =>
                IO.syncThrowable {
                  val out = transformRequest(w).asJson.noSpaces
                  requests.put(w.id, request.method)
                  logger.debug(s"${request.method -> "method"}, ${w.id -> "id"}: Prepared request $encoded")
                  wsClient.send(out)
                  w.id
                }
                  .flatMap {
                    id =>
                      val onTimeout = new RuntimeException() // TODO: ZIO interface isn't nice
                      IO.sync {
                        id
                      }
                        .flatMap {
                          id =>
                            Option(responses.get(id)) match {
                              case None =>
                                IO.fail(onTimeout)
                              case Some(value) =>
                                IO.point(Some(value))
                            }
                        }
                        .retryFor[Option[IRTMuxResponse]](None)(id => id)(timeout)
                        .flatMap {
                          case Some(value) =>
                            logger.debug(s"${request.method -> "method"}, $id: Have response: $value")
                            IO.point(value)

                          case None =>
                            IO.terminate(new TimeoutException(s"${request.method -> "method"}, $id: No response in $timeout"))
                        }
                  }
           }
        }
    }

    protected def transformRequest(request: RpcRequest): RpcRequest = request
  }

}
