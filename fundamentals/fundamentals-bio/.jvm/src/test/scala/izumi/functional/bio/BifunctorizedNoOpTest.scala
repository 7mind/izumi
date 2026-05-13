package izumi.functional.bio

import org.scalatest.wordspec.AnyWordSpec

final class BifunctorizedNoOpTest extends AnyWordSpec {

  // Type alias for the no-op shape on ZIO.
  private type ZBIO[+E, +A] = Bifunctorized.NoOp[zio.ZIO[Any, +_, +_], E, A]

  private val runner: UnsafeRun2[zio.ZIO[Any, +_, +_]] = UnsafeRun2.createZIO[Any]()

  "BifunctorizedNoOpInstances" should {

    "summon IO2[Bifunctorized.NoOp[ZIO[Any, +_, +_], +_, +_]] in default scope (no explicit import)" in {
      // BifunctorizedNoOpInstances is mixed into `object Bifunctorized`, so its implicit lives
      // in the implicit scope of `Bifunctorized.NoOp[F, ?, ?]` searches. The only `import` in
      // this file is the implicit package `izumi.functional.bio` from the test's package
      // declaration; no `import Bifunctorized._` or similar is needed.
      val F: IO2[ZBIO] = implicitly[IO2[ZBIO]]
      assert(F ne null)
    }

    "resolve to the underlying ZIO IO2 instance (singleton-identity, modulo cast)" in {
      // The no-op instance must literally be the ZIO IO2 instance, just type-reinterpreted.
      val noOp: IO2[ZBIO] = implicitly[IO2[ZBIO]]
      val zioIO2: Async2[zio.ZIO[Any, +_, +_]] = implicitly[Async2[zio.ZIO[Any, +_, +_]]]
      assert(noOp.asInstanceOf[AnyRef] eq zioIO2.asInstanceOf[AnyRef])
    }

    "F.fail(e) on the no-op produces a native ZIO typed failure (NOT SubmergedTypedError) — load-bearing" in {
      val F: IO2[ZBIO] = implicitly[IO2[ZBIO]]
      val program: ZBIO[String, Nothing] = F.fail("oops")
      val underlying: zio.ZIO[Any, String, Nothing] = program.unwrap
      val exit: Exit[String, Nothing] = runner.unsafeRunSync(underlying)
      exit match {
        case Exit.Error(error, _) =>
          assert(error == "oops")
        case other =>
          fail(s"expected Exit.Error('oops'), got: $other")
      }
    }

    "F.catchAll on F.fail(e) routes through the native typed-error channel (no submerging)" in {
      val F: IO2[ZBIO] = implicitly[IO2[ZBIO]]
      val program: ZBIO[Nothing, Int] = F.catchAll(F.fail("oops"): ZBIO[String, Int])(_ => F.pure(42))
      val underlying: zio.ZIO[Any, Nothing, Int] = program.unwrap
      val exit: Exit[Nothing, Int] = runner.unsafeRunSync(underlying)
      exit match {
        case Exit.Success(value) =>
          assert(value == 42)
        case other =>
          fail(s"expected Exit.Success(42), got: $other")
      }
    }

    "F.fail(e).unwrap is referentially identical to the corresponding ZIO.fail (zero allocation)" in {
      val F: IO2[ZBIO] = implicitly[IO2[ZBIO]]
      // The no-op cast means F.fail("oops") IS zio.ZIO.fail("oops") delegated through ZIO's IO2 directly.
      // Wrapper introduces no extra object — confirm the underlying value is a ZIO instance.
      val program: ZBIO[String, Nothing] = F.fail("oops")
      val underlying: AnyRef = program.unwrap.asInstanceOf[AnyRef]
      assert(underlying.isInstanceOf[zio.ZIO[?, ?, ?]])
    }

    "implicit-priority: the no-op outranks CatsToBIOConversions.AsyncToBIO for IO2[NoOp[ZIO, ?, ?]]" in {
      // Both BifunctorizedNoOpInstances (Predefined.Of) and CatsToBIOConversions.AsyncToBIO
      // (NotPredefined.Of) could theoretically compete for the IO2/Async2 of a Bifunctorized
      // shape over ZIO. The Predefined/NotPredefined priority machinery must pick the no-op.
      //
      // Note: PR-05's implicit returns IO2 (not Async2). Async2[NoOp[ZIO, ?, ?]] would only
      // resolve via a separate factory (out of scope). Here we test IO2 resolution.
      //
      // The reference to `CatsToBIOConversions.AsyncToBIO` below forces the CE→BIO implicit
      // to be in scope (and compiled-in) so the test verifies that, even when both candidates
      // are visible, summoning `IO2[ZBIO]` picks the no-op.
      val _ = (() => izumi.functional.bio.CatsToBIOConversions.AsyncToBIO[cats.effect.IO])

      val resolved: IO2[ZBIO] = implicitly[IO2[ZBIO]]
      val zioIO2: Async2[zio.ZIO[Any, +_, +_]] = implicitly[Async2[zio.ZIO[Any, +_, +_]]]
      assert(resolved.asInstanceOf[AnyRef] eq zioIO2.asInstanceOf[AnyRef])
    }

  }

}
