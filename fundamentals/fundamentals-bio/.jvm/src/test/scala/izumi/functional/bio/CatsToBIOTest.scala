package izumi.functional.bio

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import izumi.functional.bio.CatsToBIOConversions._
import org.scalatest.wordspec.AnyWordSpec

import scala.util.{Failure, Success, Try}

final class CatsToBIOTest extends AnyWordSpec {

  // Type alias for ergonomics in tests.
  private type BIO[+E, +A] = Bifunctorized[IO, E, A]

  // Summon the CE→BIO instance for cats.effect.IO. Used by all cases below.
  private val F: Async2[BIO] = implicitly[Async2[BIO]]

  // Run a BIO[E, A] by unwrapping to IO[A]; defects/submerged errors surface as exceptions.
  private def runUnwrapTry[E, A](b: BIO[E, A]): Try[A] = Try(b.unwrap.unsafeRunSync())

  "CatsToBIO via CatsToBIOConversions.AsyncToBIO" should {

    "summon an Async2[Bifunctorized[cats.effect.IO, +_, +_]] from cats.effect.IO" in {
      // Pure summoner check — nothing more. Compile-time guarantee.
      val _ : Async2[BIO] = implicitly[Async2[BIO]]
      assert(F ne null)
    }

    "round-trip a typed error through fail / catchAll (Goal 2: fail then catch)" in {
      val program: BIO[Nothing, Int] = F.catchAll(F.fail("boom"): BIO[String, Int])(_ => F.pure(0))
      val result = program.unwrap.unsafeRunSync()
      assert(result == 0)
    }

    "propagate an uncaught typed error through unwrap as SubmergedTypedError[F] carrying the payload" in {
      // SubmergedTypedError.unapply takes a TagK[F] type-param; call it explicitly.
      val program: BIO[String, Int] = F.fail("payload-string")
      runUnwrapTry(program) match {
        case Failure(t) =>
          assert(SubmergedTypedError.unapply[IO](t).contains("payload-string"))
        case Success(v) =>
          fail(s"expected failure carrying SubmergedTypedError[IO], got success($v)")
      }
    }

    "propagate terminate(t) RAW (not wrapped in SubmergedTypedError) — Goal 2's defect rule" in {
      val defect = new RuntimeException("kaboom")
      val program: BIO[Nothing, Nothing] = F.terminate(defect)
      runUnwrapTry(program) match {
        case Failure(t) =>
          assert(t eq defect, s"expected raw defect, got: $t")
        case Success(_) =>
          fail("expected failure, got success")
      }
    }

    "propagate sync(throw …) RAW (defect, not submerged)" in {
      val defect = new IllegalStateException("sync-throw")
      val program: BIO[Nothing, Int] = F.sync(throw defect)
      runUnwrapTry(program) match {
        case Failure(t) =>
          assert(t eq defect, s"expected raw defect, got: $t")
        case Success(v) =>
          fail(s"expected failure, got success($v)")
      }
    }

    "isolate typed errors per TagK[F]: SubmergedTypedError[OtherF] is NOT caught by catchAll[E] of Bifunctorized[F, …]" in {
      // OtherF is distinct from IO. A SubmergedTypedError[OtherF] thrown inside the IO-via-BIO
      // pipeline must NOT be discriminated as a typed error for F = IO.
      trait OtherF[A]
      val foreign = SubmergedTypedError[OtherF]("foreign-payload")

      // Inject the foreign error via F.terminate (Goal 2: terminate stays raw, even when the raw is a SubmergedTypedError of a different F).
      val program: BIO[String, Int] = F.catchAll(F.terminate(foreign): BIO[String, Int])(_ => F.pure(-1))

      runUnwrapTry(program) match {
        case Failure(t) =>
          // The foreign SubmergedTypedError must propagate untouched: catchAll[String] did NOT match it,
          // and `terminate` did not submerge it for F=IO (Goal 2). Identity equality nails it down.
          assert(t eq foreign, s"expected the foreign SubmergedTypedError raw, got: $t")
        case Success(v) =>
          fail(s"foreign typed error should not be caught by catchAll[String] for F=IO, got success($v)")
      }
    }

    "deliver pure(a).flatMap is law-abiding (smoke test for the BIO instance)" in {
      val program: BIO[Nothing, Int] = F.flatMap(F.pure(1): BIO[Nothing, Int])(x => F.pure(x + 1))
      assert(program.unwrap.unsafeRunSync() == 2)
    }

    "syncThrowable { throw t } caught by catchAll[Throwable] recovers via the submerged path" in {
      val defect = new RuntimeException("sync-throw-typed")
      val program: BIO[Nothing, Int] = F.catchAll(F.syncThrowable[Int](throw defect): BIO[Throwable, Int])(_ => F.pure(0))
      val result = program.unwrap.unsafeRunSync()
      assert(result == 0)
    }

    "syncBlocking { throw t } unhandled raises SubmergedTypedError[IO] carrying the throwable" in {
      val cause = new IllegalStateException("blocking-typed")
      // BlockingIO2[BIO] is not exposed as a separate implicit; cast the Async2 instance which
      // implements the intersection at runtime (see CatsToBIO.asyncToBIO return type).
      val blocking: BlockingIO2[BIO] = F.asInstanceOf[BlockingIO2[BIO]]
      val program: BIO[Throwable, Int] = blocking.syncBlocking[Int](throw cause)
      runUnwrapTry(program) match {
        case Failure(t) =>
          assert(SubmergedTypedError.unapply[IO](t).contains(cause))
        case Success(v) =>
          fail(s"expected failure carrying SubmergedTypedError[IO], got success($v)")
      }
    }

    "fromFuture(failed) caught by catchAll[Throwable] recovers via the submerged path" in {
      import scala.concurrent.{ExecutionContext, Future}
      val cause = new RuntimeException("future-typed")
      val program: BIO[Nothing, Int] = F.catchAll(
        F.fromFuture[Int]((_: ExecutionContext) => Future.failed(cause)): BIO[Throwable, Int]
      )(_ => F.pure(0))
      val result = program.unwrap.unsafeRunSync()
      assert(result == 0)
    }

  }

}
