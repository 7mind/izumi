package izumi.idealingua.runtime.rpc.http4s

import java.time.ZonedDateTime
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.{ConcurrentHashMap, ConcurrentLinkedDeque, TimeUnit, TimeoutException}

import fs2.concurrent.Queue
import io.circe.Json
import io.circe.syntax._
import izumi.functional.bio.BIOApplicative
import izumi.fundamentals.platform.language.Quirks
import izumi.fundamentals.platform.time.IzTime
import izumi.fundamentals.platform.uuid.UUIDGen
import izumi.idealingua.runtime.rpc._
import logstage.IzLogger
import org.http4s.AuthedRequest
import org.http4s.websocket.WebSocketFrame
import org.http4s.websocket.WebSocketFrame.{Ping, Text}

import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

trait WebsocketClientContext[F[+_, +_], ClientId, Ctx] {
  def requestState: RequestState[F]

  def id: WsClientId[ClientId]

  def enqueue(method: IRTMethodId, data: Json): F[Throwable, RpcPacketId]

  def finish(): F[Throwable, Unit]
}

trait WsSessionsStorage[F[+_, +_], ClientId, Ctx] {
  def addClient(id: WsSessionId, ctx: WebsocketClientContext[F, ClientId, Ctx]): F[Throwable, WebsocketClientContext[F, ClientId, Ctx]]

  def deleteClient(id: WsSessionId): F[Throwable, WebsocketClientContext[F, ClientId, Ctx]]

  def allClients(): F[Throwable, Seq[WebsocketClientContext[F, ClientId, Ctx]]]

  def buzzersFor(clientId: ClientId): F[Throwable, Option[IRTDispatcher[F]]]
}

trait WsSessionListener[F[+_, +_], ClientId] {
  def onSessionOpened(context: WsClientId[ClientId]): F[Throwable, Unit]

  def onClientIdUpdate(context: WsClientId[ClientId], old: WsClientId[ClientId]): F[Throwable, Unit]

  def onSessionClosed(context: WsClientId[ClientId]): F[Throwable, Unit]
}

object WsSessionListener {
  def empty[F[+_, +_]: BIOApplicative, ClientId]: WsSessionListener[F, ClientId] = new WsSessionListener[F, ClientId] {
    import izumi.functional.bio.F
    override def onSessionOpened(context: WsClientId[ClientId]): F[Throwable, Unit] = F.unit
    override def onClientIdUpdate(context: WsClientId[ClientId], old: WsClientId[ClientId]): F[Throwable, Unit] = F.unit
    override def onSessionClosed(context: WsClientId[ClientId]): F[Throwable, Unit] = F.unit
  }
}

trait WsContextProvider[F[+_, +_], Ctx, ClientId] {
  def toContext(id: WsClientId[ClientId], initial: Ctx, packet: RpcPacket): F[Throwable, Ctx]

  def toId(initial: Ctx, currentId: WsClientId[ClientId], packet: RpcPacket): F[Throwable, Option[ClientId]]

  // TODO: we use this to mangle with authorization but it's dirty
  def handleEmptyBodyPacket(id: WsClientId[ClientId], initial: Ctx, packet: RpcPacket): F[Throwable, (Option[ClientId], F[Throwable, Option[RpcPacket]])]
}

class IdContextProvider[C <: Http4sContext](val c: C#IMPL[C]) extends WsContextProvider[C#BiIO, C#RequestContext, C#ClientId] {

  import c._

  override def handleEmptyBodyPacket(
    id: WsClientId[ClientId],
    initial: C#RequestContext,
    packet: RpcPacket
  ): C#BiIO[Throwable, (Option[ClientId], C#BiIO[Throwable, Option[RpcPacket]])] = {
    Quirks.discard(id, initial, packet)
    F.pure((None, F.pure(None)))
  }

  override def toContext(id: WsClientId[C#ClientId], initial: C#RequestContext, packet: RpcPacket): C#BiIO[Throwable, C#RequestContext] = {
    Quirks.discard(packet, id)
    F.pure(initial)
  }

  override def toId(initial: C#RequestContext, currentId: WsClientId[C#ClientId], packet: RpcPacket): C#BiIO[Throwable, Option[ClientId]] = {
    Quirks.discard(initial, packet)
    F.pure(None)
  }
}

class WsSessionsStorageImpl[C <: Http4sContext](
  val c: C#IMPL[C],
  logger: IzLogger,
  codec: IRTClientMultiplexor[C#BiIO]
) extends WsSessionsStorage[C#BiIO, C#ClientId, C#RequestContext] {

  import c._
  import izumi.functional.bio.BIO

  type WSC = WebsocketClientContext[BiIO, ClientId, RequestContext]

  protected val clients = new ConcurrentHashMap[WsSessionId, WSC]()
  protected val timeout: FiniteDuration = 20.seconds
  protected val pollingInterval: FiniteDuration = 50.millis

  override def addClient(
    id: WsSessionId,
    ctx: WebsocketClientContext[C#BiIO, C#ClientId, C#RequestContext]
  ): C#BiIO[Throwable, WebsocketClientContext[C#BiIO, C#ClientId, C#RequestContext]] = F.sync {
    logger.debug(s"Adding a client with session - $id")
    clients.put(id, ctx)
  }

  override def deleteClient(id: WsSessionId): C#BiIO[Throwable, WebsocketClientContext[C#BiIO, C#ClientId, C#RequestContext]] = F.sync {
    logger.debug(s"Deleting a client with session - $id")
    clients.remove(id)
  }

  override def allClients(): C#BiIO[Throwable, Seq[WebsocketClientContext[C#BiIO, C#ClientId, C#RequestContext]]] = F.sync {
    clients.values().asScala.toSeq
  }

  override def buzzersFor(clientId: ClientId): C#BiIO[Throwable, Option[IRTDispatcher[BiIO]]] = {
    allClients().map(_.find(_.id.id.contains(clientId))).map {
      buzzerClient =>
        logger.debug(s"Asked for buzzer for $clientId. Found: $buzzerClient")
        buzzerClient.map {
          session =>
            new IRTDispatcher[BiIO] {
              override def dispatch(request: IRTMuxRequest): BiIO[Throwable, IRTMuxResponse] = {
                for {
                  json <- codec.encode(request)
                  id <- session.enqueue(request.method, json)
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
}

class WebsocketClientContextImpl[C <: Http4sContext](
  val c: C#IMPL[C],
  val initialRequest: AuthedRequest[C#MonoIO, C#RequestContext],
  val initialContext: C#RequestContext,
  listeners: Seq[WsSessionListener[C#BiIO, C#ClientId]],
  wsSessionStorage: WsSessionsStorage[C#BiIO, C#ClientId, C#RequestContext],
  logger: IzLogger
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

  def enqueue(method: IRTMethodId, data: Json): C#MonoIO[RpcPacketId] = F.sync {
    val request = RpcPacket.buzzerRequestRndId(method, data)
    val id = request.id.get
    logger.debug(s"Enqueue $request with $id to request state & send queue")
    sendQueue.add(Text(request.asJson.noSpaces))
    requestState.request(id, method)
    id
  }

  def onWsSessionOpened(): C#MonoIO[Unit] = F.unit

  def onWsClientIdUpdate(maybeNewId: Option[ClientId], oldId: WsClientId[ClientId]): C#MonoIO[Unit] = F.sync {
    logger.debug(s"Id updated to $maybeNewId, was: ${oldId.id}")
  }

  def onWsSessionClosed(): C#MonoIO[Unit] = F.sync {
    logger.debug("Finish called. Clear request state")
  }

  protected[http4s] def updateId(maybeNewId: Option[ClientId]): C#MonoIO[Unit] = {
    val oldId = id
    maybeId.set(maybeNewId)
    val newId = id

    def notifyListeners(): C#MonoIO[Unit] = {
      onWsClientIdUpdate(maybeNewId, oldId) *>
      F.traverse_(listeners) {
        listener =>
          listener.onClientIdUpdate(newId, oldId)
      }
    }

    (newId, oldId) match {
      case (WsClientId(_, Some(prev)), WsClientId(_, Some(next))) if prev != next => notifyListeners()
      case (WsClientId(_, None), WsClientId(_, None)) => F.unit
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

  override def finish(): C#MonoIO[Unit] = {
    onWsSessionClosed() *>
    wsSessionStorage.deleteClient(sessionId) *>
    F.traverse_(listeners) {
      listener =>
        listener.onSessionClosed(id)
    } *>
    F.sync(requestState.clear())
  }

  protected[http4s] def start(): C#MonoIO[Unit] = {
    wsSessionStorage.addClient(sessionId, this) *>
    onWsSessionOpened() *>
    F.traverse_(listeners) {
      listener =>
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
