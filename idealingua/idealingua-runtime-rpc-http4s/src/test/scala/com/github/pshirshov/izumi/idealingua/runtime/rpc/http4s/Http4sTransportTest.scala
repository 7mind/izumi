package com.github.pshirshov.izumi.idealingua.runtime.rpc.http4s

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

import scala.language.reflectiveCalls


class Http4sTransportTest extends WordSpec {

  import Http4sTransportTest.Http4sTestContext._

  "Http4s transport" should {
    "support direct calls" in {

      import scala.concurrent.ExecutionContext.Implicits.global
      val builder = BlazeBuilder[IO]
        .bindHttp(port, host)
        .withWebSockets(true)
        .mountService(ioService.service, "/")
        .start

      builder.unsafeRunAsync {
        case Right(server) =>
          try {
            performTests()
          } finally {
            server.shutdownNow()
          }

        case Left(error) =>
          throw error
      }


    }
  }

  private def performTests(): Unit = {
    clientDispatcher.setupCredentials("user", "pass")

    assert(ZIOR.unsafeRun(greeterClient.greet("John", "Smith")) == "Hi, John Smith!")
    assert(ZIOR.unsafeRun(greeterClient.alternative()) == "value")
    //    assert(greeterClient.sayhi().unsafeRunSync() == "Hi!")
    //    assert(calculatorClient.sum(2, 5).unsafeRunSync() == 7)
    //
    //    val missingHandler = intercept[IRTHttpFailureException] {
    //      greeterClient.broken(HowBroken.MissingServerHandler).unsafeRunSync()
    //    }
    //    assert(missingHandler.status == Status.NotFound)
    //
    clientDispatcher.cancelCredentials()
    val forbidden = intercept[IRTUnexpectedHttpStatus] {
      ZIOR.unsafeRun(greeterClient.alternative())
    }
    assert(forbidden.status == Status.Forbidden)

    clientDispatcher.setupCredentials("user", "badpass")
    val unauthorized = intercept[IRTUnexpectedHttpStatus] {
      ZIOR.unsafeRun(greeterClient.alternative())
    }
    assert(unauthorized.status == Status.Unauthorized)
    ()

  }
}

object Http4sTransportTest {

  final case class DummyContext(ip: String, credentials: Option[Credentials])


  final class AuthCheckDispatcher2[Ctx](proxied: IRTWrappedService[zio.IO, Ctx]) extends IRTWrappedService[zio.IO, Ctx] {
    override def serviceId: IRTServiceId = proxied.serviceId

    override def allMethods: Map[IRTMethodId, IRTMethodWrapper[zio.IO, Ctx]] = proxied.allMethods.mapValues {
      method =>
        new IRTMethodWrapper[zio.IO, Ctx] with IRTResultZio {

          override val signature: IRTMethodSignature = method.signature
          override val marshaller: IRTCirceMarshaller[zio.IO] = method.marshaller

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
    private val dispatchers: Set[IRTWrappedService[zio.IO, Ctx]] = Set(greeterDispatcher).map(d => new AuthCheckDispatcher2(d))
    private val clients: Set[IRTWrappedClient[zio.IO]] = Set(GreeterServiceClientWrapped)
    val codec = new IRTClientMultiplexor(clients)
    val multiplexor = new IRTServerMultiplexor[zio.IO, Ctx](dispatchers)
  }

  object Http4sTestContext {


    //
    final val addr = IzSockets.temporaryServerAddress()
    final val port = addr.getPort
    final val host = addr.getHostName
    final val baseUri = Uri(Some(Uri.Scheme.http), Some(Uri.Authority(host = Uri.RegName(host), port = Some(port))))

    //
    final val demo = new DemoContext[DummyContext]()

    //
    final val authUser: Kleisli[OptionT[IO, ?], Request[IO], DummyContext] =
      Kleisli {
        request: Request[IO] =>
          val context = DummyContext(request.remoteAddr.getOrElse("0.0.0.0"), request.headers.get(Authorization).map(_.credentials))

          OptionT.liftF(IO(context))
      }

    final val logger = IzLogger.basic(Log.Level.Trace)
    StaticLogRouter.instance.setup(logger.receiver)
    final val rt = new Http4sRuntime[zio.IO](logger)
    final val ioService = new rt.HttpServer(demo.multiplexor, AuthMiddleware(authUser))

    //
    final val clientDispatcher = new rt.ClientDispatcher(baseUri, demo.codec) {
      val creds = new AtomicReference[Seq[Header]](Seq.empty)

      def setupCredentials(login: String, password: String): Unit = {
        creds.set(Seq(Authorization(BasicCredentials(login, password))))
      }

      def cancelCredentials(): Unit = {
        creds.set(Seq.empty)
      }

      override protected def transformRequest(request: Request[IO]): Request[IO] = {
        request.withHeaders(Headers(creds.get(): _*))
      }
    }

    final val greeterClient = new GreeterServiceClientWrapped(clientDispatcher)
  }

}
