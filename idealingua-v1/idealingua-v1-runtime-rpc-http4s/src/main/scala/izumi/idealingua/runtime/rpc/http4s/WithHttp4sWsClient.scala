package izumi.idealingua.runtime.rpc.http4s

import java.net.URI
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference

import izumi.functional.bio.BIO
import izumi.functional.bio.BIOExit
import izumi.functional.bio.BIOExit.{Error, Success, Termination}
import izumi.idealingua.runtime.rpc
import izumi.idealingua.runtime.rpc._
import izumi.logstage.api.IzLogger
import io.circe.Printer
import io.circe.parser.parse
import io.circe.syntax._
import org.asynchttpclient.netty.ws.NettyWebSocket
import org.asynchttpclient.ws.{WebSocket, WebSocketListener, WebSocketUpgradeHandler}
import izumi.fundamentals.platform.language.Quirks._

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
  protected val buzzerMuxer: IRTServerMultiplexor[C#BiIO, C#ClientContext, C#ClientMethodContext],
  protected val wsClientContextProvider: WsClientContextProvider[C#ClientContext],
  logger: IzLogger,
  printer: Printer,
) extends IRTDispatcher[C#BiIO] with AutoCloseable {

  import c._

  val requestState = new RequestState[BiIO]()

  import org.asynchttpclient.Dsl._

  private val wsc = asyncHttpClient(config())
  private val listener = new WebSocketListener() {
    override def onOpen(websocket: WebSocket): Unit = {
      logger.debug(s"WS connection open: $websocket")
    }

    override def onClose(websocket: WebSocket, code: Int, reason: String): Unit = {
      logger.debug(s"WS connection closed: $websocket, $code, $reason")
    }

    override def onError(t: Throwable): Unit = {
      logger.debug(s"WS connection errored: $t")
    }

    override def onTextFrame(payload: String, finalFragment: Boolean, rsv: Int): Unit = {
      processFrame(payload)
    }
  }


  private val connection = new AtomicReference[NettyWebSocket]()

  private def send(out: String): Unit = {
    import scala.jdk.CollectionConverters._
    connection.synchronized {
      if (connection.get() == null) {
        connection.set(wsc.prepareGet(baseUri.toString)
          .execute(new WebSocketUpgradeHandler(List(listener).asJava))
          .get())
      }
    }
    connection.get().sendTextFrame(out).discard()
  }

  override def close(): Unit = {
    wsc.close()
  }

  private def processFrame(payload: String): Unit = {
    logger.debug(s"Incoming WS message: $payload")

    val result = for {
      parsed <- F.fromEither(parse(payload))
      _ <- F.sync(logger.debug(s"parsed: $parsed"))
      decoded <- F.fromEither(parsed.as[RpcPacket])
      v <- routeResponse(decoded)
    } yield {
      v
    }

    BIORunner.unsafeRunAsyncAsEither(result) {
      case Success(PacketInfo(packetId, method)) =>
        logger.debug(s"Processed incoming packet $method: $packetId")

      case Error(error, trace) =>
        logger.error(s"Failed to process request: $error $trace")

      case Termination(cause, _, trace) =>
        logger.error(s"Failed to process request, termination: $cause $trace")
    }
  }

  protected def routeResponse(decoded: RpcPacket): BiIO[Throwable, PacketInfo] = {
    decoded match {
      case RpcPacket(RPCPacketKind.RpcResponse, Some(data), _, ref, _, _, _) =>
        requestState.handleResponse(ref, data)

      case p@RpcPacket(RPCPacketKind.BuzzRequest, Some(data), Some(id), _, Some(service), Some(method), _) =>
        val methodId = IRTMethodId(IRTServiceId(service), IRTMethodName(method))
        val packetInfo = PacketInfo(methodId, id)

        val responsePkt = for {
          maybeResponse <- buzzerMuxer.doInvoke(data, wsClientContextProvider.toContext(p), methodId)
          maybePacket <- F.pure(maybeResponse.map(r => RpcPacket.buzzerResponse(id, r)))
        } yield {
          maybePacket
        }

        for {
          maybePacket <- responsePkt.sandbox.catchAll {
            case BIOExit.Termination(exception, allExceptions, trace) =>
              logger.error(s"${packetInfo -> null}: WS processing terminated, $exception, $allExceptions, $trace")
              F.pure(Some(rpc.RpcPacket.buzzerFail(Some(id), exception.getMessage)))
            case BIOExit.Error(exception, trace) =>
              logger.error(s"${packetInfo -> null}: WS processing failed, $exception $trace")
              F.pure(Some(rpc.RpcPacket.buzzerFail(Some(id), exception.getMessage)))
          }
          maybeEncoded <- F(maybePacket.map(r => printer.print(r.asJson)))
          _ <- F {
            maybeEncoded match {
              case Some(response) =>
                logger.debug(s"${method -> "method"}, ${id -> "id"}: Prepared buzzer $response")
                send(response)
              case None =>
            }
          }
        } yield {
          packetInfo
        }

      case RpcPacket(RPCPacketKind.RpcFail, data, _, Some(ref), _, _, _) =>
        requestState.respond(ref, RawResponse.BadRawResponse())
        F.fail(new IRTGenericFailure(s"RPC failure for $ref: $data"))

      case RpcPacket(RPCPacketKind.RpcFail, data, _, None, _, _, _) =>
        F.fail(new IRTGenericFailure(s"Missing ref in RPC failure: $data"))

      case RpcPacket(RPCPacketKind.Fail, data, _, _, _, _, _) =>
        F.fail(new IRTGenericFailure(s"Critical RPC failure: $data"))

      case o =>
        F.fail(new IRTMissingHandlerException(s"No buzzer client handler for $o", o))
    }
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
          val wrapped = F.sync(RpcPacket.rpcRequestRndId(request.method, encoded))

          F.bracket(wrapped) {
            id =>
              logger.trace(s"${request.method -> "method"}, ${id -> "id"}: cleaning request state")
              F.sync(requestState.forget(id.id.get))
          } {
            w =>
              val pid = w.id.get // guaranteed to be present

              F {
                val out = printer.print(transformRequest(w).asJson)
                logger.debug(s"${request.method -> "method"}, ${pid -> "id"}: Prepared request $encoded")
                requestState.request(pid, request.method)
                send(out)
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
                          F.fail(new IRTGenericFailure(s"${request.method -> "method"}, $id: generic failure: $value"))

                        case None =>
                          F.fail(new TimeoutException(s"${request.method -> "method"}, $id: No response in $timeout"))
                      }
                }
          }
      }
  }

  protected def transformRequest(request: RpcPacket): RpcPacket = request
}
