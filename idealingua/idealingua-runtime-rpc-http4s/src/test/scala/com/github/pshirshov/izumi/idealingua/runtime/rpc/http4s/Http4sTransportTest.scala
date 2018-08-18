package com.github.pshirshov.izumi.idealingua.runtime.rpc.http4s

import java.net.URI
import java.util.concurrent.atomic.AtomicReference

import cats.data.{Kleisli, OptionT}
import cats.effect._
import com.github.pshirshov.izumi.fundamentals.platform.network.IzSockets
import com.github.pshirshov.izumi.idealingua.runtime.rpc._
import com.github.pshirshov.izumi.logstage.api.routing.StaticLogRouter
import com.github.pshirshov.izumi.logstage.api.{IzLogger, Log}
import com.github.pshirshov.izumi.r2.idealingua.test.generated.{GreeterServiceClientWrapped, GreeterServiceServerWrapped}
import com.github.pshirshov.izumi.r2.idealingua.test.impls._
import org.http4s._
import org.http4s.headers.Authorization
import org.http4s.server.AuthMiddleware
import org.http4s.server.blaze._
import org.scalatest.WordSpec
import scalaz.zio

import scala.concurrent.TimeoutException

class Http4sTransportTest extends WordSpec {

  import Http4sTransportTest.Http4sTestContext._
  import Http4sTransportTest._

  "Http4s transport" should {
    "support direct calls" in {
      import scala.concurrent.ExecutionContext.Implicits.global
      val builder = BlazeBuilder[CIO]
        .bindHttp(port, host)
        .withWebSockets(true)
        .mountService(ioService.service, "/")
        .start

      builder.unsafeRunAsync {
        case Right(server) =>
          try {
            performTests(clientDispatcher)
            performWsTests(wsClientDispatcher())
          } finally {
            server.shutdownNow()
          }

        case Left(error) =>
          throw error
      }
    }

    //    "xxx" in {
    //      import scala.concurrent.ExecutionContext.Implicits.global
    //      val builder = BlazeBuilder[CIO]
    //        .bindHttp(8080, host)
    //        .withWebSockets(true)
    //        .mountService(ioService.service, "/")
    //        .start
    //
    //      builder.unsafeRunAsync {
    //        case Right(server) =>
    //          try {
    //            Thread.sleep(1000*1000)
    //          } finally {
    //            server.shutdownNow()
    //          }
    //
    //        case Left(error) =>
    //          throw error
    //      }
    //    }
  }

  private def performWsTests(disp: IRTDispatcher with TestDispatcher with AutoCloseable): Unit = {
    val greeterClient = new GreeterServiceClientWrapped(disp)

    disp.setupCredentials("user", "pass")

    assert(ZIOR.unsafeRun(greeterClient.greet("John", "Smith")) == "Hi, John Smith!")
    assert(ZIOR.unsafeRun(greeterClient.alternative()) == "value")

    disp.setupCredentials("user", "badpass")
    intercept[TimeoutException] {
      ZIOR.unsafeRun(greeterClient.alternative())
    }
    disp.close()
    ()

  }


  private def performTests(disp: IRTDispatcher with TestDispatcher): Unit = {
    val greeterClient = new GreeterServiceClientWrapped(disp)

    disp.setupCredentials("user", "pass")

    assert(ZIOR.unsafeRun(greeterClient.greet("John", "Smith")) == "Hi, John Smith!")
    assert(ZIOR.unsafeRun(greeterClient.alternative()) == "value")

    disp.cancelCredentials()
    val forbidden = intercept[IRTUnexpectedHttpStatus] {
      ZIOR.unsafeRun(greeterClient.alternative())
    }
    assert(forbidden.status == Status.Forbidden)

    disp.setupCredentials("user", "badpass")
    val unauthorized = intercept[IRTUnexpectedHttpStatus] {
      ZIOR.unsafeRun(greeterClient.alternative())
    }
    assert(unauthorized.status == Status.Unauthorized)
    ()

  }
}

object Http4sTransportTest {
  type ZIO[E, V] = zio.IO[E, V]
  type CIO[T] = cats.effect.IO[T]

  final case class DummyContext(ip: String, credentials: Option[Credentials])


  final class AuthCheckDispatcher2[Ctx](proxied: IRTWrappedService[ZIO, Ctx]) extends IRTWrappedService[ZIO, Ctx] {
    override def serviceId: IRTServiceId = proxied.serviceId

    override def allMethods: Map[IRTMethodId, IRTMethodWrapper[ZIO, Ctx]] = proxied.allMethods.mapValues {
      method =>
        new IRTMethodWrapper[ZIO, Ctx] with IRTResultZio {

          override val signature: IRTMethodSignature = method.signature
          override val marshaller: IRTCirceMarshaller[ZIO] = method.marshaller

          override def invoke(ctx: Ctx, input: signature.Input): zio.IO[Nothing, signature.Output] = {
            ctx match {
              case DummyContext(_, Some(BasicCredentials(user, pass))) =>
                if (user == "user" && pass == "pass") {
                  method.invoke(ctx, input.asInstanceOf[method.signature.Input]).map(_.asInstanceOf[signature.Output])
                } else {
                  zio.IO.terminate(IRTBadCredentialsException(Status.Unauthorized))
                }

              case _ =>
                zio.IO.terminate(IRTNoCredentialsException(Status.Forbidden))
            }
          }
        }
    }
  }

  class DemoContext[Ctx] {
    private val greeterService = new AbstractGreeterServer1.Impl[Ctx]
    private val greeterDispatcher = new GreeterServiceServerWrapped(greeterService)
    private val dispatchers: Set[IRTWrappedService[ZIO, Ctx]] = Set(greeterDispatcher).map(d => new AuthCheckDispatcher2(d))
    private val clients: Set[IRTWrappedClient[ZIO]] = Set(GreeterServiceClientWrapped)
    val codec = new IRTClientMultiplexor(clients)
    val multiplexor = new IRTServerMultiplexor[ZIO, Ctx](dispatchers)
  }

  object Http4sTestContext {


    //
    final val addr = IzSockets.temporaryServerAddress()
    final val port = addr.getPort
    final val host = addr.getHostName
    final val baseUri = Uri(Some(Uri.Scheme.http), Some(Uri.Authority(host = Uri.RegName(host), port = Some(port))))
    final val wsUri = new URI("ws", null, host, port, "/ws", null, null)

    //
    final val demo = new DemoContext[DummyContext]()
    final val rt = new Http4sRuntime[ZIO](makeLogger())

    //
    final val authUser: Kleisli[OptionT[CIO, ?], Request[CIO], DummyContext] =
      Kleisli {
        request: Request[CIO] =>
          val context = DummyContext(request.remoteAddr.getOrElse("0.0.0.0"), request.headers.get(Authorization).map(_.credentials))
          OptionT.liftF(IO(context))
      }


    final val wsContextProvider = new rt.WsContextProvider[DummyContext, String] {
      val knownAuthorization = new AtomicReference[Credentials](null)

      override def toContext(initial: DummyContext, packet: RpcRequest): DummyContext = {
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

      override def toId(initial: DummyContext, packet: RpcRequest): Option[String] = {
        packet.headers.get("Authorization")
          .map(Authorization.parse)
          .flatMap(_.toOption)
          .collect {
            case Authorization(BasicCredentials((user, _))) => user
          }
      }
    }

    final val ioService = new rt.HttpServer(demo.multiplexor, demo.codec, AuthMiddleware(authUser), wsContextProvider, rt.WsSessionListener.empty)

    //
    final val clientDispatcher: rt.ClientDispatcher with TestDispatcher = new rt.ClientDispatcher(baseUri, demo.codec) with TestDispatcher {
      override protected def transformRequest(request: Request[CIO]): Request[CIO] = {
        request.withHeaders(Headers(creds.get(): _*))
      }
    }

    final def wsClientDispatcher(): rt.ClientWsDispatcher with TestDispatcher = new rt.ClientWsDispatcher(wsUri, demo.codec) with TestDispatcher {
      override protected def transformRequest(request: RpcRequest): RpcRequest = {
        Option(creds.get()) match {
          case Some(value) =>
            val update = value.map(h => (h.name.value, h.value)).toMap
            request.copy(headers = request.headers ++ update)
          case None => request
        }
      }
    }

    final val greeterClient = new GreeterServiceClientWrapped(clientDispatcher)
  }

  private def makeLogger(): IzLogger = {
    val out = IzLogger.basic(Log.Level.Info, Map(
      "org.http4s" -> Log.Level.Warn
      , "org.http4s.server.blaze" -> Log.Level.Error
      , "org.http4s.blaze.channel.nio1" -> Log.Level.Crit
      , "com.github.pshirshov.izumi.idealingua.runtime.rpc.http4s" -> Log.Level.Trace
    ))
    StaticLogRouter.instance.setup(out.receiver)
    out
  }
}


