package com.github.pshirshov.izumi.idealingua.runtime.rpc.http4s

import cats._
import cats.data.{Kleisli, OptionT}
import cats.effect._
import com.github.pshirshov.izumi.fundamentals.platform.network.IzSockets
import com.github.pshirshov.izumi.idealingua.runtime.circe.{IRTClientMarshallers, IRTOpinionatedMarshalers, IRTServerMarshallers}
import com.github.pshirshov.izumi.idealingua.runtime.rpc.{IRTServerMultiplexor, _}
import com.github.pshirshov.izumi.logstage.api.IzLogger
import com.github.pshirshov.izumi.logstage.api.routing.StaticLogRouter
import com.github.pshirshov.izumi.r2.idealingua.test.generated._
import com.github.pshirshov.izumi.r2.idealingua.test.impls._
import org.http4s._
import org.http4s.client.blaze.Http1Client
import org.http4s.dsl._
import org.http4s.headers.Authorization
import org.http4s.server._
import org.http4s.server.blaze._
import org.scalatest.WordSpec

import scala.language.higherKinds


class Http4sTransportTest extends WordSpec {

  import Http4sTransportTest.Http4sTestContext._

  "Http4s transport" should {
    "support direct calls" in {

      import scala.concurrent.ExecutionContext.Implicits.global
      val builder = BlazeBuilder[IO]
        .bindHttp(port, host)
        .mountService(ioService, "/")
        .start

      builder.unsafeRunAsync {
        case Right(server) =>
          try {
            assert(greeterClient.greet("John", "Smith").unsafeRunSync() == "Hi, John Smith!")
            assert(greeterClient.sayhi().unsafeRunSync() == "Hi!")
            assert(calculatorClient.sum(2, 5).unsafeRunSync() == 7)
            //assert(greeterClient.broken(HowBroken.MissingServerHandler).unsafeRunSync() == "")
            ()
          } finally {
            server.shutdownNow()
          }

        case Left(error) =>
          throw error
      }


    }
  }
}

object Http4sTransportTest {

  final case class DummyContext(ip: String, credentials: Option[Credentials])

  class DemoContext[R[_] : IRTResult : Monad, Ctx] {
    private val greeterService = new AbstractGreeterServer.Impl[R, Ctx]
    private val calculatorService = new AbstractCalculatorServer.Impl[R, Ctx]
    private val greeterDispatcher = GreeterServiceWrapped.serverUnsafe(greeterService)
    private val calculatorDispatcher = CalculatorServiceWrapped.serverUnsafe(calculatorService)
    private val dispatchers = List(greeterDispatcher, calculatorDispatcher)

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

    final val logger = IzLogger.SimpleConsoleLogger
    StaticLogRouter.instance.setup(logger.receiver)
    final val rt = new RuntimeHttp4s[IO](logger, io, demo.sm)
    final val ioService = rt.httpService(demo.serverMuxer, AuthMiddleware(authUser))

    //
    final val clientDispatcher = rt.httpClient(Http1Client[IO]().unsafeRunSync, demo.cm, baseUri)
    final val greeterClient = GreeterServiceWrapped.clientUnsafe(clientDispatcher)
    final val calculatorClient = CalculatorServiceWrapped.clientUnsafe(clientDispatcher)
  }

}
