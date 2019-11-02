package izumi.idealingua.runtime.rpc.http4s

import java.time.ZonedDateTime
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.{ConcurrentHashMap, ConcurrentLinkedDeque, TimeUnit, TimeoutException}

import izumi.fundamentals.platform.language.Quirks
import izumi.fundamentals.platform.time.IzTime
import izumi.fundamentals.platform.uuid.UUIDGen
import izumi.idealingua.runtime.rpc._
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
  def requestState: RequestState[B]

  def id: WsClientId[ClientId]

  def enqueue(method: IRTMethodId, data: Json): RpcPacketId

  def finish(): Unit
}

trait WsSessionsStorage[B[+ _, + _], ClientId, Ctx] {
  def addClient(id: WsSessionId, ctx: WebsocketClientContext[B, ClientId, Ctx]): WebsocketClientContext[B, ClientId, Ctx]

  def deleteClient(id: WsSessionId): WebsocketClientContext[B, ClientId, Ctx]

  def allClients(): Seq[WebsocketClientContext[B, ClientId, Ctx]]

  def buzzersFor(clientId: ClientId): Option[IRTDispatcher[B]]
}

trait WsSessionListener[ClientId] {
  def onSessionOpened(context: WsClientId[ClientId]): Unit

  def onClientIdUpdate(context: WsClientId[ClientId], old: WsClientId[ClientId]): Unit

  def onSessionClosed(context: WsClientId[ClientId]): Unit
}

object WsSessionListener {
  def empty[ClientId]: WsSessionListener[ClientId] = new WsSessionListener[ClientId] {
    override def onSessionOpened(context: WsClientId[ClientId]): Unit = {}

    override def onSessionClosed(context: WsClientId[ClientId]): Unit = {}

    override def onClientIdUpdate(context: WsClientId[ClientId], old: WsClientId[ClientId]): Unit = {}
  }
}


trait WsContextProvider[B[+ _, + _], Ctx, ClientId] {
  def toContext(id: WsClientId[ClientId], initial: Ctx, packet: RpcPacket): B[Throwable, Ctx]

  def toId(initial: Ctx, currentId: WsClientId[ClientId], packet: RpcPacket): B[Throwable, Option[ClientId]]

  // TODO: we use this to mangle with authorization but it's dirty
  def handleEmptyBodyPacket(id: WsClientId[ClientId], initial: Ctx, packet: RpcPacket): B[Throwable, (Option[ClientId], B[Throwable, Option[RpcPacket]])]
}

class IdContextProvider[C <: Http4sContext](val c: C#IMPL[C]) extends WsContextProvider[C#BiIO, C#RequestContext, C#ClientId] {

  import c._

  override def handleEmptyBodyPacket(id: WsClientId[ClientId], initial: C#RequestContext, packet: RpcPacket): C#BiIO[Throwable, (Option[ClientId], C#BiIO[Throwable, Option[RpcPacket]])] = {
    Quirks.discard(id, initial, packet)
    F.pure((None, F.pure(None)))
  }

  override def toContext(id: WsClientId[C#ClientId], initial: C#RequestContext, packet: RpcPacket): C#BiIO[Throwable, C#RequestContext] = {
    Quirks.discard(packet, id)
    F.pure(initial)
  }

  override def toId(initial:  C#RequestContext, currentId:  WsClientId[C#ClientId], packet:  RpcPacket): C#BiIO[Throwable, Option[ClientId]] = {
    Quirks.discard(initial, packet)
    F.pure(None)
  }
}

class WsSessionsStorageImpl[C <: Http4sContext]
(
  val c: C#IMPL[C]
  , logger: IzLogger
  , codec: IRTClientMultiplexor[C#BiIO]
) extends WsSessionsStorage[C#BiIO, C#ClientId, C#RequestContext] {

  import c._
  import izumi.functional.bio.BIO

  type WSC = WebsocketClientContext[BiIO, ClientId, RequestContext]

  protected val clients = new ConcurrentHashMap[WsSessionId, WSC]()
  protected val timeout: FiniteDuration = 20.seconds
  protected val pollingInterval: FiniteDuration = 50.millis

  def addClient(id: WsSessionId, ctx: WSC): WSC = {
    logger.debug(s"Adding a client with session - $id")
    clients.put(id, ctx)
  }

  def deleteClient(id: WsSessionId): WSC = {
    logger.debug(s"Deleting a client with session - $id")
    clients.remove(id)
  }

  def allClients(): Seq[WSC] = {
    clients.values().asScala.toSeq
  }

  def buzzersFor(clientId: ClientId): Option[IRTDispatcher[BiIO]] = {
    val buzzerClient = allClients()
      .find(_.id.id.contains(clientId))

    logger.debug(s"Asked for buzzer for $clientId. Found: $buzzerClient")

    buzzerClient
      .map {
        session =>
          new IRTDispatcher[BiIO] {
            override def dispatch(request: IRTMuxRequest): BiIO[Throwable, IRTMuxResponse] = {
              for {
                json <- codec.encode(request)
                id <- F.sync(session.enqueue(request.method, json))
                resp <- F.bracket(F.pure(id)) {
                  id =>
                    logger.debug(s"${request.method -> "method"}, ${id -> "id"}: cleaning request state")
                    F.sync(session.requestState.forget(id))
                } {
                  id =>
                    session.requestState.poll(id, pollingInterval, timeout).flatMap {
                      case Some(value: RawResponse.GoodRawResponse) =>
                        logger.debug(s"${request.method -> "method"}, $id: Have response: $value")
                        codec.decode(value.data, value.method)

                      case Some(value: RawResponse.BadRawResponse) =>
                        logger.debug(s"${request.method -> "method"}, $id: Generic failure response: $value")
                        F.fail(new IRTGenericFailure(s"${request.method -> "method"}, $id: generic failure: $value"))

                      case None =>
                        F.fail(new TimeoutException(s"${request.method -> "method"}, $id: No response in $timeout"))
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

class WebsocketClientContextImpl[C <: Http4sContext]
(
  val c: C#IMPL[C]
  , val initialRequest: AuthedRequest[C#MonoIO, C#RequestContext]
  , val initialContext: C#RequestContext
  , listeners: Seq[WsSessionListener[C#ClientId]]
  , wsSessionStorage: WsSessionsStorage[C#BiIO, C#ClientId, C#RequestContext]
  , logger: IzLogger
) extends WebsocketClientContext[C#BiIO, C#ClientId, C#RequestContext] {

  import c._

  private val maybeId = new AtomicReference[Option[ClientId]]()
  private val sendQueue = new ConcurrentLinkedDeque[WebSocketFrame]()
  override val requestState = new RequestState()

  private val sessionId = WsSessionId(UUIDGen.getTimeUUID())
  private val openingTime: ZonedDateTime = IzTime.utcNow

  private val pingTimeout: FiniteDuration = 25.seconds
  private val queuePollTimeout: FiniteDuration = 100.millis

  private val queueBatchSize: Int = 10

  def id: WsClientId[ClientId] = WsClientId(sessionId, Option(maybeId.get()).flatten)

  def duration(): FiniteDuration = {
    val now = IzTime.utcNow

    val d = java.time.Duration.between(openingTime, now)
    FiniteDuration(d.toNanos, TimeUnit.NANOSECONDS)
  }

  def enqueue(method: IRTMethodId, data: Json): RpcPacketId = {
    val request = RpcPacket.buzzerRequestRndId(method, data)
    val id = request.id.get
    logger.debug(s"Enqueue $request with $id to request state & send queue")
    sendQueue.add(Text(request.asJson.noSpaces))
    requestState.request(id, method)
    id
  }

  def onWsSessionOpened(): Unit = {
  }

  def onWsClientIdUpdate(maybeNewId: Option[ClientId], oldId: WsClientId[ClientId]): Unit = {
    logger.debug(s"Id updated to $maybeNewId, was: ${oldId.id}")
  }

  def onWsSessionClosed(): Unit = {
    logger.debug("Finish called. Clear request state")
  }

  protected[http4s] def updateId(maybeNewId: Option[ClientId]): Unit = {
    val oldId = id
    maybeId.set(maybeNewId)
    val newId = id

    def notifyListeners(): Unit = {
      onWsClientIdUpdate(maybeNewId, oldId)
      listeners.foreach { listener =>
        listener.onClientIdUpdate(newId, oldId)
      }
    }

    (newId, oldId) match {
      case (WsClientId(_, Some(prev)), WsClientId(_, Some(next))) if prev != next => notifyListeners()
      case (WsClientId(_, None), WsClientId(_, None)) => ()
      case _ => notifyListeners()
    }
  }

  protected[http4s] val queue: MonoIO[Queue[MonoIO, WebSocketFrame]] = Queue.unbounded[MonoIO, WebSocketFrame]

  protected[http4s] val outStream: fs2.Stream[MonoIO, WebSocketFrame] =
    fs2.Stream.awakeEvery[MonoIO](queuePollTimeout) >> {
      val messages = (0 until queueBatchSize).map(_ => Option(sendQueue.poll())).collect {
        case Some(m) => m
      }
      if (messages.nonEmpty)
        logger.debug(s"Going to stream $messages from send queue")
      fs2.Stream.emits(messages)
    }

  protected[http4s] val pingStream: fs2.Stream[MonoIO, WebSocketFrame] =
    fs2.Stream.awakeEvery[MonoIO](pingTimeout) >> fs2.Stream(Ping())

  override def finish(): Unit = {
    onWsSessionClosed()
    Quirks.discard(wsSessionStorage.deleteClient(sessionId))
    listeners.foreach { listener =>
      listener.onSessionClosed(id)
    }
    requestState.clear()
  }

  protected[http4s] def start(): Unit = {
    Quirks.discard(wsSessionStorage.addClient(sessionId, this))
    onWsSessionOpened()
    listeners.foreach { listener =>
      listener.onSessionOpened(id)
    }
  }

  override def toString: String = s"[${id.toString}, ${duration().toSeconds}s]"
}

sealed trait RawResponse

object RawResponse {

  case class GoodRawResponse(data: Json, method: IRTMethodId) extends RawResponse

  case class BadRawResponse() extends RawResponse // This needs to be extended: https://github.com/7mind/izumi/issues/355
}
