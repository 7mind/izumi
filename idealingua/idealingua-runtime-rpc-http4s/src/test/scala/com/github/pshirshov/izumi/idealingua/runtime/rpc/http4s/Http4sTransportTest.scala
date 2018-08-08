package com.github.pshirshov.izumi.idealingua.runtime.rpc.http4s

import java.util.concurrent.atomic.AtomicReference

import cats._
import cats.data.{Kleisli, OptionT}
import cats.effect._
import com.github.pshirshov.izumi.fundamentals.platform.network.IzSockets
import com.github.pshirshov.izumi.idealingua.runtime.circe.{IRTClientMarshallers, IRTOpinionatedMarshalers, IRTServerMarshallers}
import com.github.pshirshov.izumi.idealingua.runtime.rpc.{IRTServerMultiplexor, _}
import com.github.pshirshov.izumi.logstage.api.routing.StaticLogRouter
import com.github.pshirshov.izumi.logstage.api.{IzLogger, Log}
import com.github.pshirshov.izumi.r2.idealingua.test.generated._
import com.github.pshirshov.izumi.r2.idealingua.test.impls._
import org.http4s._
import org.http4s.dsl._
import org.http4s.headers.Authorization
import org.http4s.server._
import org.http4s.server.blaze._
import org.scalatest.WordSpec

import scala.language.{higherKinds, reflectiveCalls}


class Http4sTransportTest extends WordSpec {

  import Http4sTransportTest.Http4sTestContext._

  "Http4s transport" should {
    "support direct calls" in {

      import scala.concurrent.ExecutionContext.Implicits.global
      val builder = BlazeBuilder[IO]
        .bindHttp(port, host)
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
    assert(greeterClient.greet("John", "Smith").unsafeRunSync() == "Hi, John Smith!")
    assert(greeterClient.sayhi().unsafeRunSync() == "Hi!")
    assert(calculatorClient.sum(2, 5).unsafeRunSync() == 7)

    val missingHandler = intercept[IRTHttpFailureException] {
      greeterClient.broken(HowBroken.MissingServerHandler).unsafeRunSync()
    }
    assert(missingHandler.status == Status.NotFound)

    clientDispatcher.cancelCredentials()

    val unauthorized = intercept[IRTHttpFailureException] {
      calculatorClient.sum(403, 0).unsafeRunSync()
    }
    assert(unauthorized.status == Status.Forbidden)
    ()
  }
}

object Http4sTransportTest {

  final case class DummyContext(ip: String, credentials: Option[Credentials])

  final class AuthCheckDispatcher[Ctx, R[_]](proxied: IRTUnsafeDispatcher[Ctx, R]) extends IRTUnsafeDispatcher[Ctx, R] {
    override def identifier: IRTServiceId = proxied.identifier

    override def dispatchUnsafe(input: UnsafeInput): MaybeOutput = {
      input.context match {
        case DummyContext(_, Some(BasicCredentials(user, pass))) =>
          if (user == "user" && pass == "pass") {
            proxied.dispatchUnsafe(input)
          } else {
            Left(DispatchingFailure.NoHandler)
          }

        case _ =>
          Left(DispatchingFailure.Rejected)
      }
    }
  }

  class DemoContext[R[_] : IRTResult : Monad, Ctx] {
    private val greeterService = new AbstractGreeterServer.Impl[R, Ctx]
    private val calculatorService = new AbstractCalculatorServer.Impl[R, Ctx]
    private val greeterDispatcher = GreeterServiceWrapped.serverUnsafe(greeterService)
    private val calculatorDispatcher = CalculatorServiceWrapped.serverUnsafe(calculatorService)
    private val dispatchers = List(greeterDispatcher, calculatorDispatcher).map(d => new AuthCheckDispatcher(d))

    private final val codecs = List(GreeterServiceWrapped, CalculatorServiceWrapped)
    private final val marsh = IRTOpinionatedMarshalers(codecs)

    final val serverMuxer = new IRTServerMultiplexor(dispatchers)
    final val cm: IRTClientMarshallers = marsh
    final val sm: IRTServerMarshallers = marsh
  }

  object Http4sTestContext {

    import com.github.pshirshov.izumi.idealingua.runtime.cats.RuntimeCats._

    //
    final val addr = IzSockets.temporaryServerAddress()
    final val port = addr.getPort
    final val host = addr.getHostName
    final val baseUri = Uri(Some(Uri.Scheme.http), Some(Uri.Authority(host = Uri.RegName(host), port = Some(port))))

    //
    final val demo = new DemoContext[IO, DummyContext]()

    //
    final val authUser: Kleisli[OptionT[IO, ?], Request[IO], DummyContext] =
      Kleisli {
        request: Request[IO] =>
          val context = DummyContext(request.remoteAddr.getOrElse("0.0.0.0"), request.headers.get(Authorization).map(_.credentials))

          OptionT.liftF(IO(context))
      }

    final val logger = IzLogger.basic(Log.Level.Info)
    StaticLogRouter.instance.setup(logger.receiver)
    final val rt = new Http4sRuntime(io, logger, demo.sm,  demo.cm)
    final val ioService = new rt.HttpServer(demo.serverMuxer, AuthMiddleware(authUser))

    //
    final val clientDispatcher = new rt.ClientDispatcher(baseUri) {
      val creds = new AtomicReference[Seq[Header]](Seq.empty)
      def setupCredentials(login: String, password: String): Unit = {
        creds.set(Seq(Authorization(BasicCredentials(login, password))))
      }

      def cancelCredentials(): Unit = {
        creds.set(Seq.empty)
      }

      override protected def transformRequest(request: Request[IO]): Request[IO] = {
        request.withHeaders(Headers(creds.get() :_*))
      }
    }

    final val greeterClient = GreeterServiceWrapped.clientUnsafe(clientDispatcher)
    final val calculatorClient = CalculatorServiceWrapped.clientUnsafe(clientDispatcher)
  }

}
