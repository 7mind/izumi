package izumi.idealingua.runtime.rpc.http4s

import izumi.fundamentals.platform.language.Quirks._
import izumi.functional.bio.BIOExit.{Error, Success, Termination}
import izumi.idealingua.runtime.rpc._
import izumi.r2.idealingua.test.generated.{GreeterServiceClientWrapped, GreeterServiceMethods}
import org.http4s._
import org.http4s.server.Router
import org.http4s.server.blaze._
import org.scalatest.wordspec.AnyWordSpec
import zio.Task
import zio.interop.catz._
import zio.interop.catz.implicits._

class Http4sTransportTest extends AnyWordSpec {

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
            case Termination(exception: IRTUnexpectedHttpStatus, _, _) =>
              assert(exception.status == Status.Forbidden)
            case o =>
              fail(s"Expected IRTGenericFailure but got $o")
          }

          //
          disp.setupCredentials("user", "badpass")
          BIOR.unsafeRunSyncAsEither(greeterClient.alternative()) match {
            case Termination(exception: IRTUnexpectedHttpStatus, _, _) =>
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

        BIOR.unsafeRunSyncAsEither(ioService.wsSessionStorage.buzzersFor("user")) match {
          case Success(buzzers) =>
            buzzers.foreach {
              buzzer =>
                val client = new GreeterServiceClientWrapped(buzzer)
                assert(BIOR.unsafeRun(client.greet("John", "Buzzer")) == "Hi, John Buzzer!")
            }
          case v => fail(s"Expected success result but got $v")
        }

        disp.setupCredentials("user", "badpass")
        BIOR.unsafeRunSyncAsEither(greeterClient.alternative()) match {
          case Termination(_: IRTGenericFailure, _, _) =>
          case o =>
            fail(s"Expected IRTGenericFailure but got $o")
        }

        disp.close()
        ()
      }
    }
  }

  def withServer(f: => Unit): Unit = {
    import org.http4s.implicits._
    val router = Router("/" -> ioService.service).orNotFound
    val io = BlazeServerBuilder[rt.MonoIO]
      .bindHttp(port, host)
      .withWebSockets(true)
      .withHttpApp(router)
      .stream
      .evalMap(_ => Task(f))
      .compile.drain

    BIOR.unsafeRun(io.interruptChildren)
  }

  def checkBadBody(body: String, disp: IRTDispatcher[rt.BiIO] with TestHttpDispatcher): Unit = {
    val dummy = IRTMuxRequest(IRTReqBody((1, 2)), GreeterServiceMethods.greet.id)
    val badJson = BIOR.unsafeRunSyncAsEither(disp.sendRaw(dummy, body.getBytes))
    badJson match {
      case Error(value: IRTUnexpectedHttpStatus, _) =>
        assert(value.status == Status.BadRequest).discard()
      case Error(value, _) =>
        fail(s"Unexpected error: $value")
      case Success(value) =>
        fail(s"Unexpected success: $value")
      case Termination(exception, _, _) =>
        fail("Unexpected failure", exception)
    }
  }
}
