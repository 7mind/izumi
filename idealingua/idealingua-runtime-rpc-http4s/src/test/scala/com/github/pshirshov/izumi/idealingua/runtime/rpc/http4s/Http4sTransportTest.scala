package com.github.pshirshov.izumi.idealingua.runtime.rpc.http4s

import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks._
import com.github.pshirshov.izumi.functional.bio.BIO._
import com.github.pshirshov.izumi.functional.bio.BIOExit.{Error, Success, Termination}
import com.github.pshirshov.izumi.idealingua.runtime.rpc._
import com.github.pshirshov.izumi.r2.idealingua.test.generated.{GreeterServiceClientWrapped, GreeterServiceMethods}
import org.http4s._
import org.http4s.server.blaze._
import org.scalatest.WordSpec
import scalaz.zio.interop.Task
import scalaz.zio.interop.catz._

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
          BIOR.unsafeRunSyncAsEither(greeterClient.alternative()) match {
            case Termination(exception: IRTUnexpectedHttpStatus, _) =>
              assert(exception.status == Status.Forbidden)
            case o =>
              fail(s"Expected IRTGenericFailure but got $o")
          }

          //
          disp.setupCredentials("user", "badpass")
          BIOR.unsafeRunSyncAsEither(greeterClient.alternative()) match {
            case Termination(exception: IRTUnexpectedHttpStatus, _) =>
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
          case Termination(_: IRTGenericFailure, _) =>
          case o =>
            fail(s"Expected IRTGenericFailure but got $o")
        }

        disp.close()
        ()
      }
    }
  }

  def withServer(f: => Unit): Unit = {
    val io = BlazeBuilder[rt.MonoIO]
      .bindHttp(port, host)
      .withWebSockets(true)
      .mountService(ioService.service, "/")
      .stream
      // FIXME: parEvalMap/mapAsync/parJoin etc. doesn't work on ZIO https://github.com/functional-streams-for-scala/fs2/issues/1396
//      .mapAsync(1)(_ => Task(f))
      .evalMap(_ => Task(f))
      .compile.drain

    BIOR.unsafeRun(io.supervise)
  }

  def checkBadBody(body: String, disp: IRTDispatcher[rt.BiIO] with TestHttpDispatcher): Unit = {
    val dummy = IRTMuxRequest(IRTReqBody((1, 2)), GreeterServiceMethods.greet.id)
    val badJson = BIOR.unsafeRunSyncAsEither(disp.sendRaw(dummy, body.getBytes))
    badJson match {
      case Error(value: IRTUnexpectedHttpStatus) =>
        assert(value.status == Status.BadRequest).discard()
      case Error(value) =>
        fail(s"Unexpected error: $value")
      case Success(value) =>
        fail(s"Unexpected success: $value")
      case Termination(exception, _) =>
        fail("Unexpected failure", exception)
    }
  }
}
