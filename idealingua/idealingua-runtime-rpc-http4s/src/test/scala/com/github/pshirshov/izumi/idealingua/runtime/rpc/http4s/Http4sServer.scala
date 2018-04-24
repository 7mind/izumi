package com.github.pshirshov.izumi.idealingua.runtime.rpc.http4s

import cats._
import cats.data.{Kleisli, OptionT}
import cats.effect._
import com.github.pshirshov.izumi.idealingua.runtime.circe.{IRTClientMarshallers, IRTOpinionatedMarshalers, IRTServerMarshallers}
import com.github.pshirshov.izumi.r2.idealingua.runtime.rpc._
import com.github.pshirshov.izumi.r2.idealingua.test.generated._
import com.github.pshirshov.izumi.r2.idealingua.test.impls._
import org.http4s._
import org.http4s.client.blaze.Http1Client
import org.http4s.dsl._
import org.http4s.dsl.io._
import org.http4s.server._
import org.http4s.server.blaze._
import org.scalatest.WordSpec

import scala.language.higherKinds


case class DummyContext(ip: String)

class Demo[R[_] : IRTServiceResult : Monad, Ctx] {

  final val serverMuxer = {
    val greeterService = new AbstractGreeterServer.Impl[R, Ctx]
    val calculatorService = new AbstractCalculatorServer.Impl[R, Ctx]
    val greeterDispatcher = GreeterServiceWrapped.serverUnsafe(greeterService)
    val calculatorDispatcher = CalculatorServiceWrapped.serverUnsafe(calculatorService)
    val dispatchers = List(greeterDispatcher, calculatorDispatcher)
    new IRTServerMultiplexor(dispatchers)
  }

  final val codecs = List(GreeterServiceWrapped, CalculatorServiceWrapped)

  private final val marsh = IRTOpinionatedMarshalers(codecs)
  final val cm: IRTClientMarshallers = marsh
  final val sm: IRTServerMarshallers = marsh
}

object Definitions {

  import com.github.pshirshov.izumi.idealingua.runtime.cats.RuntimeCats._

  final val demo = new Demo[IO, DummyContext]()

  final val authUser: Kleisli[OptionT[IO, ?], Request[IO], DummyContext] =
    Kleisli {
      request: Request[IO] =>
        val context = DummyContext(request.remoteAddr.getOrElse("0.0.0.0"))

        OptionT.liftF(IO(context))
    }

  final val port = 8080
  final val host = "localhost"
  final val baseUri = Uri(Some(Uri.Scheme.http), Some(Uri.Authority(host = Uri.RegName(host), port = Some(port))))
  final val rt = new RuntimeHttp4s[IO]
  final val ioService = rt.httpService(demo.serverMuxer, AuthMiddleware(authUser), demo.sm, io)
  final val clientDispatcher = rt.httpClient(Http1Client[IO]().unsafeRunSync, demo.cm)(rt.requestBuilder(baseUri))

  final val greeterClient = GreeterServiceWrapped.clientUnsafe(clientDispatcher)
  final val calculatorClient = CalculatorServiceWrapped.clientUnsafe(clientDispatcher)
}


class Http4sTransportTest extends WordSpec {


  "Http4s transport" should {
    "support direct calls" in {
      import Definitions._

      val builder = BlazeBuilder[IO]
        .bindHttp(port, host)
        .mountService(ioService, "/")
        .start

      builder.unsafeRunAsync {
        case Right(server) =>
          assert(greeterClient.greet("John", "Smith").unsafeRunSync() == "Hi, John Smith!")
          assert(greeterClient.sayhi().unsafeRunSync() == "Hi!")
          server.shutdownNow()

        case Left(error) =>
          throw error
      }


    }
  }
}
