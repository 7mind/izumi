package com.github.pshirshov.izumi.idealingua.runtime.rpc.http4s

import cats.effect.IO
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks._
import com.github.pshirshov.izumi.functional.bio.BIO._
import com.github.pshirshov.izumi.idealingua.runtime.rpc._
import com.github.pshirshov.izumi.r2.idealingua.test.generated.{GreeterServiceClientWrapped, GreeterServiceMethods}
import org.http4s._
import org.http4s.server.blaze._
import org.scalatest.WordSpec

import scala.util.{Failure, Success}

class Http4sTransportTest extends WordSpec {

  import fixtures._
  import Http4sTestContext._
  import RT._

  "Http4s transport" should {
    "support http" in {
        withServer {
          val disp = clientDispatcher()
          val greeterClient = new GreeterServiceClientWrapped(disp)

          disp.setupCredentials("user", "pass")

          assert(BIOR.unsafeRun(greeterClient.greet("John", "Smith")) == "Hi, John Smith!")
          assert(BIOR.unsafeRun(greeterClient.alternative()) == "value")

          checkBadBody("{}", disp)
          checkBadBody("{unparseable", disp)


          disp.cancelCredentials()
          val forbidden = intercept[IRTUnexpectedHttpStatus] {
            BIOR.unsafeRun(greeterClient.alternative())
          }
          assert(forbidden.status == Status.Forbidden)

          //
          disp.setupCredentials("user", "badpass")
          BIOR.unsafeRunSyncAsEither(greeterClient.alternative()) match {
            case Failure(exception: IRTUnexpectedHttpStatus) =>
              assert(exception.status == Status.Unauthorized)
            case o =>
              fail(s"Expected IRTGenericFailure but got $o")
          }

          ()
        }
    }

    "support websockets" in {
      withServer {
        val disp = wsClientDispatcher()

        val greeterClient = new GreeterServiceClientWrapped(disp)

        disp.setupCredentials("user", "pass")

        assert(BIOR.unsafeRun(greeterClient.greet("John", "Smith")) == "Hi, John Smith!")
        assert(BIOR.unsafeRun(greeterClient.alternative()) == "value")

        ioService.wsSessionStorage.buzzersFor("user").foreach {
          buzzer =>
            val client = new GreeterServiceClientWrapped(buzzer)
            assert(BIOR.unsafeRun(client.greet("John", "Buzzer")) == "Hi, John Buzzer!")
        }

        disp.setupCredentials("user", "badpass")
        BIOR.unsafeRunSyncAsEither(greeterClient.alternative()) match {
          case Failure(exception: IRTGenericFailure) =>
          case o =>
            fail(s"Expected IRTGenericFailure but got $o")
        }

        disp.close()
        ()
      }
    }
  }

  def withServer(f: => Unit): Unit = {
    BlazeBuilder[rt.CatsIO]
      .bindHttp(port, host)
      .withWebSockets(true)
      .mountService(ioService.service, "/")
      .stream
      .mapAsync(1)(_ => IO(f))
      .compile.drain.unsafeRunSync()
  }

  def checkBadBody(body: String, disp: IRTDispatcher[rt.BiIO] with TestHttpDispatcher): Unit = {
    val dummy = IRTMuxRequest(IRTReqBody((1, 2)), GreeterServiceMethods.greet.id)
    val badJson = BIOR.unsafeRunSyncAsEither(disp.sendRaw(dummy, body.getBytes))
    badJson match {
      case Success(Left(value: IRTUnexpectedHttpStatus)) =>
        assert(value.status == Status.BadRequest).discard()
      case Success(value) =>
        fail(s"Unexpected success: $value")
      case Failure(exception) =>
        fail("Unexpected failure", exception)
    }
  }
}
