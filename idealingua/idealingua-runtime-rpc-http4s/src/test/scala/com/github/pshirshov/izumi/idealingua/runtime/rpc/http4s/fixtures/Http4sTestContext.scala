package com.github.pshirshov.izumi.idealingua.runtime.rpc.http4s.fixtures

import java.net.URI
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

import cats.data.{Kleisli, OptionT}
import cats.effect.{ContextShift, IO, Timer}
import com.github.pshirshov.izumi.fundamentals.platform.network.IzSockets
import com.github.pshirshov.izumi.idealingua.runtime.rpc.http4s.{BIORunner, Http4sRuntime, WsClientContextProvider}
import com.github.pshirshov.izumi.idealingua.runtime.rpc.{IRTMuxRequest, IRTMuxResponse, RpcPacket}
import com.github.pshirshov.izumi.logstage.api.routing.StaticLogRouter
import com.github.pshirshov.izumi.logstage.api.{IzLogger, Log}
import com.github.pshirshov.izumi.r2.idealingua.test.generated.GreeterServiceClientWrapped
import org.http4s.headers.Authorization
import org.http4s.server.AuthMiddleware
import org.http4s.{BasicCredentials, Credentials, Headers, Request, Uri}
import com.github.pshirshov.izumi.idealingua.runtime.bio.BIO._

object Http4sTestContext {
  //
  final val addr = IzSockets.temporaryServerAddress()
  final val port = addr.getPort
  final val host = addr.getHostName
  final val baseUri = Uri(Some(Uri.Scheme.http), Some(Uri.Authority(host = Uri.RegName(host), port = Some(port))))
  final val wsUri = new URI("ws", null, host, port, "/ws", null, null)

  //
  final val demo = new DummyServices[BiIO, DummyRequestContext]()

  import scala.concurrent.ExecutionContext.Implicits.global

  implicit val contextShift: ContextShift[CIO] = IO.contextShift(global)
  implicit val timer: Timer[CIO] = IO.timer(global)
  implicit val BIOR: BIORunner[BiIO] = BIORunner.createZIO(Executors.newWorkStealingPool())

  final val rt = {
    new Http4sRuntime[BiIO, CIO](makeLogger(), global)
  }

  //
  final val authUser: Kleisli[OptionT[CIO, ?], Request[CIO], DummyRequestContext] =
    Kleisli {
      request: Request[CIO] =>
        val context = DummyRequestContext(request.remoteAddr.getOrElse("0.0.0.0"), request.headers.get(Authorization).map(_.credentials))
        OptionT.liftF(IO(context))
    }


  final val wsContextProvider = new rt.WsContextProvider[DummyRequestContext, String] {
    val knownAuthorization = new AtomicReference[Credentials](null)

    override def toContext(initial: DummyRequestContext, packet: RpcPacket): DummyRequestContext = {
      initial.credentials match {
        case Some(value) =>
          knownAuthorization.compareAndSet(null, value)
        case None =>
      }

      val maybeAuth = packet.headers.get("Authorization")

      maybeAuth.map(Authorization.parse).flatMap(_.toOption) match {
        case Some(value) =>
          knownAuthorization.set(value.credentials)
        case None =>
      }
      initial.copy(credentials = Option(knownAuthorization.get()))
    }

    override def toId(initial: DummyRequestContext, packet: RpcPacket): Option[String] = {
      packet.headers.get("Authorization")
        .map(Authorization.parse)
        .flatMap(_.toOption)
        .collect {
          case Authorization(BasicCredentials((user, _))) => user
        }
    }
  }

  final val ioService = new rt.HttpServer(demo.Server.multiplexor, demo.Server.codec, AuthMiddleware(authUser), wsContextProvider, rt.WsSessionListener.empty)

  //
  final def clientDispatcher(): rt.ClientDispatcher with TestHttpDispatcher =
    new rt.ClientDispatcher(baseUri, demo.Client.codec)
      with TestHttpDispatcher {

      override def sendRaw(request: IRTMuxRequest, body: Array[Byte]): BiIO[Throwable, IRTMuxResponse] = {
        val req = buildRequest(baseUri, request, body)
        runRequest(handleResponse(request, _), req)
      }

      override protected def transformRequest(request: Request[CIO]): Request[CIO] = {
        request.withHeaders(Headers(creds.get(): _*))
      }
    }

  final val wsClientContextProvider = new WsClientContextProvider[Unit] {
    override def toContext(packet: RpcPacket): Unit = ()
  }

  final def wsClientDispatcher(): rt.ClientWsDispatcher[Unit] with TestDispatcher =
    new rt.ClientWsDispatcher(wsUri, demo.Client.codec, demo.Client.buzzerMultiplexor, wsClientContextProvider)
      with TestDispatcher {
      override protected def transformRequest(request: RpcPacket): RpcPacket = {
        Option(creds.get()) match {
          case Some(value) =>
            val update = value.map(h => (h.name.value, h.value)).toMap
            request.copy(headers = request.headers ++ update)
          case None => request
        }
      }
    }

  final val greeterClient = new GreeterServiceClientWrapped(clientDispatcher())

  private def makeLogger(): IzLogger = {
    val out = IzLogger(Log.Level.Info, levels = Map(
      "org.http4s" -> Log.Level.Warn
      , "org.http4s.server.blaze" -> Log.Level.Error
      , "org.http4s.blaze.channel.nio1" -> Log.Level.Crit
      , "com.github.pshirshov.izumi.idealingua.runtime.rpc.http4s" -> Log.Level.Trace
    ))
    StaticLogRouter.instance.setup(out.receiver)
    out
  }
}
