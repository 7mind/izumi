package com.github.pshirshov.izumi.idealingua.runtime.rpc.http4s.fixtures

import java.net.URI
import java.util.concurrent.atomic.AtomicReference

import cats.data.{Kleisli, OptionT}
import cats.effect.IO
import com.github.pshirshov.izumi.functional.bio.BIO._
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks
import com.github.pshirshov.izumi.fundamentals.platform.network.IzSockets
import com.github.pshirshov.izumi.idealingua.runtime.rpc.http4s._
import com.github.pshirshov.izumi.idealingua.runtime.rpc.{IRTMuxRequest, IRTMuxResponse, RpcPacket}
import com.github.pshirshov.izumi.r2.idealingua.test.generated.GreeterServiceClientWrapped
import io.circe.Json
import org.http4s.headers.Authorization
import org.http4s.server.AuthMiddleware
import org.http4s.{BasicCredentials, Credentials, Headers, Request, Uri}
import scalaz.zio


object Http4sTestContext {
  //
  final val addr = IzSockets.temporaryServerAddress()
  final val port = addr.getPort
  final val host = addr.getHostName
  final val baseUri = Uri(Some(Uri.Scheme.http), Some(Uri.Authority(host = Uri.RegName(host), port = Some(port))))
  final val wsUri = new URI("ws", null, host, port, "/ws", null, null)

  //

  import RT.rt
  import rt._

  final val demo = new DummyServices[rt.type#BiIO, DummyRequestContext]()


  //
  final val authUser: Kleisli[OptionT[CatsIO, ?], Request[CatsIO], DummyRequestContext] =
    Kleisli {
      request: Request[CatsIO] =>
        val context = DummyRequestContext(request.remoteAddr.getOrElse("0.0.0.0"), request.headers.get(Authorization).map(_.credentials))
        OptionT.liftF(IO(context))
    }

  final val wsContextProvider = new WsContextProvider[BiIO, DummyRequestContext, String] {
    // DON'T DO THIS IN PRODUCTION CODE !!!
    val knownAuthorization = new AtomicReference[Credentials](null)

    override def toContext(id: WsClientId[String], initial: DummyRequestContext, packet: RpcPacket): DummyRequestContext = {
      Quirks.discard(id)

      initial.credentials match {
        case Some(value) =>
          knownAuthorization.compareAndSet(null, value)
        case None =>
      }

      val maybeAuth = packet.headers.getOrElse(Map.empty).get("Authorization")

      maybeAuth.map(Authorization.parse).flatMap(_.toOption) match {
        case Some(value) =>
          knownAuthorization.set(value.credentials)
        case None =>
      }
      initial.copy(credentials = Option(knownAuthorization.get()))
    }

    override def toId(initial: DummyRequestContext, currentId: WsClientId[String], packet: RpcPacket): Option[String] = {
      packet.headers.getOrElse(Map.empty).get("Authorization")
        .map(Authorization.parse)
        .flatMap(_.toOption)
        .collect {
          case Authorization(BasicCredentials((user, _))) => user
        }
    }

    override def handleEmptyBodyPacket(id: WsClientId[String], initial: DummyRequestContext, packet: RpcPacket): (Option[String], zio.IO[Throwable, Option[RpcPacket]]) = {
      Quirks.discard(id, initial)

      packet.headers.getOrElse(Map.empty).get("Authorization") match {
        case Some(value) if value.isEmpty =>
          // here we may clear internal state
          None -> BIO.now(None)

        case Some(_) =>
          toId(initial, id, packet) match {
            case id @ Some(_) =>
              // here we may set internal state
              id -> BIO.now(packet.ref.map {
                ref =>
                  RpcPacket.rpcResponse(ref, Json.obj())
              })

            case None =>
              None -> BIO.now(Some(RpcPacket.rpcFail(packet.ref, "Authorization failed")))
          }

        case None =>
          None -> BIO.now(None)
      }
    }
  }

  final val storage = new WsSessionsStorageImpl[rt.type](rt.self, RT.logger, demo.Server.codec)
  final val ioService = new HttpServer[rt.type](
    rt.self,
    demo.Server.multiplexor,
    demo.Server.codec,
    AuthMiddleware(authUser),
    wsContextProvider,
    storage,
    Seq(WsSessionListener.empty[String])
    , RT.logger
    , RT.printer
  )

  final def clientDispatcher(): ClientDispatcher[rt.type] with TestHttpDispatcher =
    new ClientDispatcher[rt.DECL](rt.self, RT.logger, RT.printer, baseUri, demo.Client.codec)
      with TestHttpDispatcher {

      override def sendRaw(request: IRTMuxRequest, body: Array[Byte]): BiIO[Throwable, IRTMuxResponse] = {
        val req = buildRequest(baseUri, request, body)
        runRequest(handleResponse(request, _), req)
      }

      override protected def transformRequest(request: Request[CatsIO]): Request[CatsIO] = {
        request.withHeaders(Headers(creds.get(): _*))
      }
    }

  final val wsClientContextProvider = new WsClientContextProvider[Unit] {
    override def toContext(packet: RpcPacket): Unit = ()
  }

  final def wsClientDispatcher(): ClientWsDispatcher[rt.type] with TestDispatcher =
    new ClientWsDispatcher[rt.type](rt.self, wsUri, demo.Client.codec, demo.Client.buzzerMultiplexor, wsClientContextProvider, RT.logger, RT.printer)
      with TestDispatcher {
      override protected def transformRequest(request: RpcPacket): RpcPacket = {
        Option(creds.get()) match {
          case Some(value) =>
            val update = value.map(h => (h.name.value, h.value)).toMap
            request.copy(headers = Some(request.headers.getOrElse(Map.empty) ++ update))
          case None =>
            request
        }
      }
    }

  final val greeterClient = new GreeterServiceClientWrapped(clientDispatcher())

}
