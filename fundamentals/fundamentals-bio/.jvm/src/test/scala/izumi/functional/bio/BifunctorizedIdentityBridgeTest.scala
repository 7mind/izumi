package izumi.functional.bio

import izumi.fundamentals.platform.functional.Identity
import org.scalatest.wordspec.AnyWordSpec

import scala.util.{Failure, Success, Try}

final class BifunctorizedIdentityBridgeTest extends AnyWordSpec {

  // Type alias for ergonomics inside test bodies.
  private type FIdent[+E, +A] = Bifunctorized.IdentityBifunctorized[E, A]

  // Summon the M2 instance (mixed into `object Bifunctorized` via BifunctorizedNoOpInstances).
  private val F: IO2[FIdent] = implicitly[IO2[FIdent]]

  "Bifunctorized.IdentityBifunctorized" should {

    "round-trip a pure value (bifunctorizeIdentity / debifunctorizeIdentity)" in {
      val wrapped: FIdent[Throwable, Int] = Bifunctorized.bifunctorizeIdentity[Int](42)
      val unwrapped: Identity[Int] = Bifunctorized.debifunctorizeIdentity(wrapped)
      assert(unwrapped == 42)
    }

    "F.pure(a) is a value that runs to a" in {
      val program: FIdent[Nothing, Int] = F.pure(42)
      val widened: FIdent[Throwable, Int] = program
      assert(Bifunctorized.debifunctorizeIdentity(widened) == 42)
    }

    "F.fail(e) followed by F.catchAll recovers the typed error" in {
      val failed: FIdent[String, Int] = F.fail("oops")
      val recovered: FIdent[Nothing, Int] = F.catchAll(failed)(_ => F.pure(0))
      val widened: FIdent[Throwable, Int] = recovered
      assert(Bifunctorized.debifunctorizeIdentity(widened) == 0)
    }

    "F.fail(throwable) un-caught re-raises on debifunctorize" in {
      val cause = new RuntimeException("rt")
      val failed: FIdent[Throwable, Int] = F.fail(cause)
      Try(Bifunctorized.debifunctorizeIdentity(failed)) match {
        case Failure(t) =>
          // MiniBIO's autoRun rethrows via Exit#toThrowable; for typed Throwable errors that
          // collapses to the original throwable (Exit.Error.toThrowableEither = Left(ev(error))).
          assert(t eq cause, s"expected raw cause, got: $t")
        case Success(v) =>
          fail(s"expected failure, got success($v)")
      }
    }

    "F.terminate(t) un-caught re-raises the defect on debifunctorize" in {
      val defect = new IllegalStateException("kaboom")
      // Widen the program success channel to Int so the `Try[Int]` below has a reachable Success case
      // under Scala 2.13's -Wdeadcode (a Try[Nothing] makes Success unreachable).
      val program: FIdent[Throwable, Int] = F.terminate(defect)
      Try(Bifunctorized.debifunctorizeIdentity(program)) match {
        case Failure(t) =>
          assert(t eq defect, s"expected raw defect, got: $t")
        case Success(v) =>
          fail(s"expected failure, got success($v)")
      }
    }

    "F.sync(throw t) un-caught propagates the defect on debifunctorize" in {
      val defect = new IllegalStateException("sync-throw")
      val program: FIdent[Nothing, Int] = F.sync[Int](throw defect)
      val widened: FIdent[Throwable, Int] = program
      Try(Bifunctorized.debifunctorizeIdentity(widened)) match {
        case Failure(t) =>
          assert(t eq defect, s"expected raw defect, got: $t")
        case Success(v) =>
          fail(s"expected failure, got success($v)")
      }
    }

    "flatMap chain evaluates left-to-right" in {
      val program: FIdent[Nothing, Int] =
        F.flatMap(F.pure(1): FIdent[Nothing, Int]) { i =>
          F.flatMap(F.pure(i + 1): FIdent[Nothing, Int])(j => F.pure(j + 1))
        }
      val widened: FIdent[Throwable, Int] = program
      assert(Bifunctorized.debifunctorizeIdentity(widened) == 3)
    }

    "F.pure suspends side effects (lawful behavior — repairs the IO1Identity unlawfulness)" in {
      // IO1Identity.maybeSuspend used to evaluate eagerly; the MiniBIO-backed
      // IdentityBifunctorized must suspend until `debifunctorizeIdentity` runs the MiniBIO.
      var counter = 0
      val program: FIdent[Nothing, Int] = F.sync { counter += 1; counter }
      // Constructing the program must not have incremented yet.
      assert(counter == 0, s"side effect leaked at construction time: counter=$counter")
      val widened: FIdent[Throwable, Int] = program
      val firstRun: Int = Bifunctorized.debifunctorizeIdentity(widened)
      assert(firstRun == 1)
      // Running again must increment again (each run evaluates the suspended block fresh).
      val secondRun: Int = Bifunctorized.debifunctorizeIdentity(widened)
      assert(secondRun == 2)
      assert(counter == 2)
    }

  }

}
