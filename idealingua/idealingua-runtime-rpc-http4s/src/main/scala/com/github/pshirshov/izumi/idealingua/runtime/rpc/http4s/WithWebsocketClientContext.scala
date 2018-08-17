package com.github.pshirshov.izumi.idealingua.runtime.rpc.http4s

import java.time.ZonedDateTime
import java.util.concurrent.{ConcurrentHashMap, TimeUnit}
import java.util.concurrent.atomic.AtomicReference

import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks
import com.github.pshirshov.izumi.fundamentals.platform.time.IzTime
import com.github.pshirshov.izumi.fundamentals.platform.uuid.UUIDGen
import com.github.pshirshov.izumi.idealingua.runtime.rpc.RpcRequest
import fs2.async
import fs2.async.mutable.Queue
import org.http4s.AuthedRequest
import org.http4s.websocket.WebsocketBits._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

trait WithWebsocketClientContext {
  this: Http4sContext =>

  trait WsSessionListener[Ctx, ClientId] {
    def onSessionOpened(context: WebsocketClientContext[ClientId, Ctx]): Unit
    def onSessionClosed(context: WebsocketClientContext[ClientId, Ctx]): Unit
  }

  object WsSessionListener {
    def empty[Ctx, ClientId]: WsSessionListener[Ctx, ClientId] = new WsSessionListener[Ctx, ClientId] {
      override def onSessionOpened(context: WebsocketClientContext[ClientId, Ctx]): Unit = {}

      override def onSessionClosed(context: WebsocketClientContext[ClientId, Ctx]): Unit = {}
    }
  }

  class WebsocketClientContext[ClientId, Ctx]
  (
    val initialRequest: AuthedRequest[CIO, Ctx]
    , val initialContext: Ctx
    , listener: WsSessionListener[Ctx, ClientId]
    , sessions: ConcurrentHashMap[WsSessionId, WebsocketClientContext[ClientId, Ctx]]
  ) {
    private val pingTimeout: FiniteDuration = 25.seconds

    private val queuePollTimeout: FiniteDuration = 100.millis

    private val maybeId = new AtomicReference[ClientId]()

    val openingTime: ZonedDateTime = IzTime.utcNow

    def duration(): FiniteDuration = {
      val now = IzTime.utcNow

      val d = java.time.Duration.between(openingTime, now)
      FiniteDuration(d.toNanos, TimeUnit.NANOSECONDS)
    }

    val sessionId = WsSessionId(UUIDGen.getTimeUUID)

    val queue: CIO[Queue[CIO, WebSocketFrame]] = async.unboundedQueue[CIO, WebSocketFrame]

    val outStream: fs2.Stream[CIO, WebSocketFrame] =
      fs2.Stream.awakeEvery[CIO](queuePollTimeout)
        .flatMap {
          d =>
            if (false) {
              fs2.Stream.apply(Text("test"))
            } else {
              fs2.Stream.empty
            }
        }

    val pingStream: fs2.Stream[CIO, WebSocketFrame] =
      fs2.Stream.awakeEvery[CIO](pingTimeout)
        .flatMap {
          _ =>
            fs2.Stream(Ping())
        }

    def updateId(maybeNewId: Option[ClientId]): Unit = {
      maybeNewId.foreach(maybeId.set)
    }

    def id: WsClientId[ClientId] = WsClientId(sessionId, Option(maybeId.get()))

    def finish(): Unit = {
      Quirks.discard(sessions.remove(id))
      listener.onSessionClosed(this)
    }

    def start(): Unit = {
      Quirks.discard(sessions.put(sessionId, this))
      listener.onSessionOpened(this)
    }


    override def toString: String = s"[${id.toString}, ${duration().toSeconds}s]"
  }

  trait WsContextProvider[Ctx, ClientId] {
    def toContext(initial: Ctx, packet: RpcRequest): Ctx
    def toId(initial: Ctx, packet: RpcRequest): Option[ClientId]
  }

  object WsContextProvider {
    def id[Ctx, ClientId]: WsContextProvider[Ctx, ClientId] = new WsContextProvider[Ctx, ClientId] {
      override def toContext(initial: Ctx, packet: RpcRequest): Ctx = {
        Quirks.discard(packet)
        initial
      }

      override def toId(initial: Ctx, packet: RpcRequest): Option[ClientId] = {
        Quirks.discard(initial, packet)
        None
      }
    }
  }
}
