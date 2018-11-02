package com.github.pshirshov.izumi.idealingua.runtime.rpc.http4s

import java.time.ZonedDateTime
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.{ConcurrentHashMap, ConcurrentLinkedDeque, TimeUnit, TimeoutException}

import com.github.pshirshov.izumi.functional.bio.BIOAsync
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks
import com.github.pshirshov.izumi.fundamentals.platform.time.IzTime
import com.github.pshirshov.izumi.fundamentals.platform.uuid.UUIDGen
import com.github.pshirshov.izumi.idealingua.runtime.rpc._
import fs2.concurrent.Queue
import io.circe.Json
import io.circe.syntax._
import logstage.IzLogger
import org.http4s.AuthedRequest
import org.http4s.websocket.WebSocketFrame
import org.http4s.websocket.WebSocketFrame.{Ping, Text}

import scala.collection.JavaConverters._
import scala.concurrent.duration._

trait WebsocketClientContext[B[+ _, + _], ClientId, Ctx] {
  val requestState: RequestState[B]

  def id: WsClientId[ClientId]

  def enqueue(method: IRTMethodId, data: Json): RpcPacketId
}

trait WsSessionsStorage[B[+ _, + _], ClientId, Ctx] {
  def addClient(id: WsSessionId, ctx: WebsocketClientContext[B, ClientId, Ctx]): WebsocketClientContext[B, ClientId, Ctx]

  def deleteClient(id: WsSessionId): WebsocketClientContext[B, ClientId, Ctx]

  def allClients(): Seq[WebsocketClientContext[B, ClientId, Ctx]]

  def buzzersFor(clientId: ClientId): Option[IRTDispatcher[B]]
}

trait WsSessionListener[ClientId] {
  def onSessionOpened(context: WsClientId[ClientId]): Unit

  def onClientIdUpdate(context: WsClientId[ClientId]): Unit

  def onSessionClosed(context: WsClientId[ClientId]): Unit
}

object WsSessionListener {
  def empty[ClientId]: WsSessionListener[ClientId] = new WsSessionListener[ClientId] {
    override def onSessionOpened(context: WsClientId[ClientId]): Unit = {}

    override def onSessionClosed(context: WsClientId[ClientId]): Unit = {}

    override def onClientIdUpdate(context: WsClientId[ClientId]): Unit = {}
  }
}

trait WsContextProvider[Ctx, ClientId] {
  def toContext(initial: Ctx, packet: RpcPacket): Ctx

  def toId(initial: Ctx, packet: RpcPacket): Option[ClientId]
}

object WsContextProvider {
  def id[Ctx, ClientId]: WsContextProvider[Ctx, ClientId] = new WsContextProvider[Ctx, ClientId] {
    override def toContext(initial: Ctx, packet: RpcPacket): Ctx = {
      Quirks.discard(packet)
      initial
    }

    override def toId(initial: Ctx, packet: RpcPacket): Option[ClientId] = {
      Quirks.discard(initial, packet)
      None
    }
  }
}

class WsSessionsStorageImpl[C <: Http4sContext]
(val c: C#IMPL[C], logger: IzLogger, codec: IRTClientMultiplexor[C#BiIO]) extends WsSessionsStorage[C#BiIO, C#ClientId, C#RequestContext] {

  import c._

  import com.github.pshirshov.izumi.functional.bio.BIO._

  type WSC = WebsocketClientContext[BiIO, ClientId, RequestContext]

  protected val clients = new ConcurrentHashMap[WsSessionId, WSC]()
  protected val timeout: FiniteDuration = 20.seconds
  protected val pollingInterval: FiniteDuration = 50.millis

  def addClient(id: WsSessionId, ctx: WSC): WSC = {
    clients.put(id, ctx)
  }

  def deleteClient(id: WsSessionId): WSC = {
    clients.remove(id)
  }

  def allClients(): Seq[WSC] = {
    clients.values().asScala.toSeq
  }

  def buzzersFor(clientId: ClientId): Option[IRTDispatcher[BiIO]] = {
    allClients()
      .find(_.id.id.contains(clientId))
      .map {
        sess =>
          new IRTDispatcher[BiIO] {
            override def dispatch(request: IRTMuxRequest): BiIO[Throwable, IRTMuxResponse] = {
              for {
                session <- BIO.point(sess)
                json <- codec.encode(request)
                id <- BIO.sync(session.enqueue(request.method, json))
                resp <- BIO.bracket[Throwable, RpcPacketId, IRTMuxResponse](BIO.point(id)) {
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

                            case Some(value: RawResponse.BadRawResponse) =>
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
  }
}

class WebsocketClientContextImpl[C <: Http4sContext, B[+ _, + _] : BIOAsync]
(
  val c: C#IMPL[C],
  val initialRequest: AuthedRequest[C#CatsIO, C#RequestContext]
  , val initialContext: C#RequestContext
  , listeners: Seq[WsSessionListener[C#ClientId]]
  , wsSessionStorage: WsSessionsStorage[B, C#ClientId, C#RequestContext]
) extends WebsocketClientContext[B, C#ClientId, C#RequestContext] {

  import c._

  private val sessionId = WsSessionId(UUIDGen.getTimeUUID)

  private val maybeId = new AtomicReference[ClientId]()

  def id: WsClientId[ClientId] = WsClientId(sessionId, Option(maybeId.get()))

  val openingTime: ZonedDateTime = IzTime.utcNow

  def duration(): FiniteDuration = {
    val now = IzTime.utcNow

    val d = java.time.Duration.between(openingTime, now)
    FiniteDuration(d.toNanos, TimeUnit.NANOSECONDS)
  }

  def enqueue(method: IRTMethodId, data: Json): RpcPacketId = {
    val request = RpcPacket.buzzerRequest(method, data)
    val id = request.id.get
    sendQueue.add(Text(request.asJson.noSpaces))
    requestState.request(id, method)
    id
  }

  protected[http4s] def updateId(maybeNewId: Option[ClientId]): Unit = {
    maybeNewId.foreach { i =>
      maybeId.set(i)
      listeners.foreach { listener =>
        listener.onClientIdUpdate(id)
      }
    }
  }

  private val pingTimeout: FiniteDuration = 25.seconds

  private val queuePollTimeout: FiniteDuration = 100.millis

  private val queueBatchSize: Int = 10

  private val sendQueue = new ConcurrentLinkedDeque[WebSocketFrame]()

  protected[http4s] val queue: CatsIO[Queue[CatsIO, WebSocketFrame]] = Queue.unbounded[CatsIO, WebSocketFrame]

  protected[http4s] val outStream: fs2.Stream[CatsIO, WebSocketFrame] =
    fs2.Stream.awakeEvery[CatsIO](queuePollTimeout) >> {
      val messages = (0 until queueBatchSize).map(_ => Option(sendQueue.poll())).collect {
        case Some(m) => m
      }
      fs2.Stream(messages: _*)
    }

  protected[http4s] val pingStream: fs2.Stream[CatsIO, WebSocketFrame] =
    fs2.Stream.awakeEvery[CatsIO](pingTimeout) >> fs2.Stream(Ping())

  protected[http4s] def finish(): Unit = {
    Quirks.discard(wsSessionStorage.deleteClient(sessionId))
    listeners.foreach { listener =>
      listener.onSessionClosed(id)
    }
    requestState.clear()
  }

  protected[http4s] def start(): Unit = {
    Quirks.discard(wsSessionStorage.addClient(sessionId, this))
    listeners.foreach { listener =>
      listener.onSessionOpened(id)
    }
  }

  val requestState = new RequestState()

  override def toString: String = s"[${id.toString}, ${duration().toSeconds}s]"
}

sealed trait RawResponse

object RawResponse {

  case class GoodRawResponse(data: Json, method: IRTMethodId) extends RawResponse

  case class BadRawResponse() extends RawResponse // This needs to be extended: https://github.com/pshirshov/izumi-r2/issues/355
}
