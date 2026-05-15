package izumi.functional.bio

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import izumi.functional.bio.CatsToBIOConversions._
import org.scalatest.wordspec.AnyWordSpec

import scala.util.{Failure, Success, Try}

/** Transparent (de-)submerging tests for [[Bifunctorized.bifunctorize]] /
  * [[Bifunctorized.debifunctorize]] via the cats-mediated implicit conversions
  * in [[CatsToBIOConversions]] (`bifunctorizeSubmerging`, `debifunctorizeUnSubmerging`).
  *
  * These tests pin the spec behavior from `bifunctorization.md` §"Conversion of
  * effect values":
  *
  * > "the Throwable error must be Submerged, converted into a typed error during `bifunctorize`."
  *
  * > "In `debifunctorize`, a typed error must be de-Submerged, unwrapped, as its expected
  * > to be in order for monofunctor's native methods to work with it."
  *
  * The cats-mediated implicit conversions live in the user's import scope when they
  * `import izumi.functional.bio.CatsToBIOConversions.*`. Import scope outranks
  * `Bifunctorized.{bifunctorize,debifunctorize}Conversion` (companion-of-RHS), so
  * cats-mediated submerging wins at expected-type sites for any `F[_]` that has both
  * `cats.ApplicativeError[F, Throwable]` AND `izumi.reflect.TagK[F]`. The direct method
  * `Bifunctorized.bifunctorize` itself remains identity (Goal 4: zero-cost for real bifunctors).
  *
  * Identity special-case round-trip (Goal 3) is covered in
  * `BifunctorizedIdentityBridgeTest.scala`; this file focuses on the cats-mediated path
  * for `cats.effect.IO` and similar monofunctors with `cats.ApplicativeError[F, Throwable]`.
  */
final class BifunctorizeTransparencyTest extends AnyWordSpec {

  private type BIO[+E, +A] = Bifunctorized[IO, E, A]

  // Pinned, ambient instance for `catchAll` / `fail` / `terminate`.
  private val F: Async2[BIO] = implicitly[Async2[BIO]]

  "Bifunctorized.bifunctorize via cats-mediated implicit conversion (CatsToBIOConversions)" should {

    "spec round-trip A: raw IO.raiseError lifted to Bifunctorized at the assignment site is catchable as a typed Throwable error" in {
      val rt = new RuntimeException("rt")
      // Assignment-site implicit conversion fires `bifunctorizeSubmerging` from import scope —
      // IO's raw Throwable becomes a typed BIO error (SubmergedTypedError[IO](rt)).
      val b: BIO[Throwable, Int] = IO.raiseError[Int](rt)
      val recovered: BIO[Nothing, Int] = F.catchAll(b)(_ => F.pure(0))
      val result = recovered.unwrap.unsafeRunSync()
      assert(result == 0)
    }

    "spec round-trip A (idempotency): re-bifunctorizing a Bifunctorized doesn't double-wrap" in {
      val rt = new RuntimeException("rt2")
      // F.fail produces a Bifunctorized whose error is already SubmergedTypedError[IO](rt).
      val failed: BIO[Throwable, Int] = F.fail(rt)
      // Re-lift the underlying IO[Int] through the implicit conversion: SubmergedTypedError.apply
      // is idempotent on TagK match, so the carrier IS still SubmergedTypedError[IO](rt)
      // (not nested).
      val raw: IO[Int] = failed.unwrap
      val again: BIO[Throwable, Int] = raw
      Try(again.unwrap.unsafeRunSync()) match {
        case Failure(t) =>
          // Match the same payload via the SubmergedTypedError unapply, and confirm the cause
          // is the original rt (not a nested SubmergedTypedError).
          assert(SubmergedTypedError.unapply[IO](t).contains(rt))
        case Success(v) =>
          fail(s"expected failure, got success($v)")
      }
    }

    "spec round-trip B: debifunctorize unwraps SubmergedTypedError[IO] back to the raw IO Throwable" in {
      val rt = new RuntimeException("rt-de")
      val failed: BIO[Throwable, Int] = F.fail(rt)
      // Assignment-site implicit conversion fires `debifunctorizeUnSubmerging` from import scope —
      // SubmergedTypedError[IO](rt) becomes rt.
      val io: IO[Int] = failed
      Try(io.unsafeRunSync()) match {
        case Failure(t) =>
          // Must be the raw rt — NOT a SubmergedTypedError[IO] wrapper.
          assert(t eq rt, s"expected raw rt, got: ${t.getClass.getName}: $t")
          // Negative check: assert the runtime class is NOT SubmergedTypedError (class-level check
          // sidesteps Scala 3's higher-kinded type argument requirement on `_`/`Any`).
          assert(
            !classOf[SubmergedTypedError[Nothing]].isAssignableFrom(t.getClass),
            s"debifunctorize did not un-wrap SubmergedTypedError: $t",
          )
        case Success(v) =>
          fail(s"expected failure, got success($v)")
      }
    }

    "Defect passthrough: F.terminate(rt) survives debifunctorize as the raw Throwable" in {
      val rt = new IllegalStateException("kaboom")
      // F.terminate routes to F.raiseError(rt) directly — no SubmergedTypedError wrapping (Goal 2).
      // Widen typed-error channel to Throwable and success channel to Int (not Nothing) — `BIO[E, Nothing]`
      // triggers a Scala 2.13 view-conversion edge case that misfires; Int sidesteps it without
      // changing the runtime semantics (the program still never produces a Success).
      val program: BIO[Throwable, Int] = F.terminate(rt)
      // Implicit conversion to IO via debifunctorizeUnSubmerging — but rt isn't a SubmergedTypedError,
      // so adaptError's PartialFunction won't match and rt propagates unchanged.
      val io: IO[Int] = program
      Try(io.unsafeRunSync()) match {
        case Failure(t) =>
          assert(t eq rt, s"expected raw defect, got: ${t.getClass.getName}: $t")
        case Success(v) =>
          fail(s"expected failure, got success($v)")
      }
    }

    "Defect passthrough: raw IO defect via bifunctorize submerges (becomes typed); debifunctorize then un-submerges back to raw" in {
      val rt = new RuntimeException("round-trip")
      // `IO.delay(throw rt)` is a raw IO that fails with rt at run time.
      val raw: IO[Int] = IO.delay[Int](throw rt)
      // bifunctorize-submerging implicit lifts it to BIO — rt is captured as a typed error.
      val b: BIO[Throwable, Int] = raw
      // catchAll[Throwable] catches the typed error and recovers.
      val recovered: BIO[Nothing, Int] = F.catchAll(b)(t => if (t eq rt) F.pure(0) else F.terminate(t))
      assert(recovered.unwrap.unsafeRunSync() == 0)
      // Without the catchAll, the round-trip back through debifunctorize-un-submerging produces raw rt.
      val io2: IO[Int] = b
      Try(io2.unsafeRunSync()) match {
        case Failure(t) =>
          assert(t eq rt, s"round-trip back to IO should produce raw rt, got: $t")
        case Success(v) =>
          fail(s"expected failure, got success($v)")
      }
    }

    "Real bifunctor no-op: Bifunctorized.bifunctorize(zio) eq zio (Goal 4 preserved — direct method call, not implicit conversion)" in {
      // The direct method `Bifunctorized.bifunctorize` does NOT take cats implicits, so it remains
      // type-level identity even with `import CatsToBIOConversions._` in scope.
      val raw: zio.ZIO[Any, Throwable, Int] = zio.ZIO.succeed(42)
      val wrapped: Bifunctorized[zio.ZIO[Any, Throwable, *], Throwable, Int] = Bifunctorized.bifunctorize(raw)
      assert(wrapped.asInstanceOf[AnyRef] eq raw.asInstanceOf[AnyRef])
    }

    "Identity bridge round-trip (regression check)" in {
      // bifunctorizeIdentity/debifunctorizeIdentity follow a DIFFERENT path (MiniBIO carrier,
      // not cats-mediated) — this case just confirms that the Identity path is not regressed
      // by the CatsToBIOConversions additions.
      val ib: Bifunctorized.IdentityBifunctorized[Throwable, Int] = Bifunctorized.bifunctorizeIdentity[Int](42)
      val out: Int = Bifunctorized.debifunctorizeIdentity(ib)
      assert(out == 42)
    }

  }

}
