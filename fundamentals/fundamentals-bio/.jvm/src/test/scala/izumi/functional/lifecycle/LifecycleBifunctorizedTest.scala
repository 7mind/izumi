package izumi.functional.lifecycle

import izumi.functional.bio.{Bifunctorized, Exit, IO2, UnsafeRun2}
import org.scalatest.wordspec.AnyWordSpec
import zio.ZIO

import java.util.concurrent.atomic.AtomicInteger

/** M3-PR1: end-to-end tests for the parallel BIO entry-point [[LifecycleBifunctorized]].
  *
  * The bifunctor under test is `ZIO[Any, +_, +_]`, wrapped through the no-op shape
  * `Bifunctorized.NoOp[ZIO[Any, +_, +_], +_, +_]` whose `IO2` instance comes from
  * [[izumi.functional.bio.BifunctorizedNoOpInstances#bifunctorIsAlreadyBifunctor]].
  */
final class LifecycleBifunctorizedTest extends AnyWordSpec {

  // Bifunctor under test (with type-level Any environment elided as in BifunctorizedNoOpTest).
  type ZBIO[+E, +A] = ZIO[Any, E, A]

  // Monofunctor carrier of the produced Lifecycle (i.e. F[Throwable, _] = ZIO[Any, Throwable, _]).
  type ZThrow[A] = ZBIO[Throwable, A]

  // The BIO instance on the wrapper. Implicit search picks the high-priority no-op identity
  // instance and casts the existing ZIO `IO2` dictionary to `IO2[NoOp[ZIO, +_, +_]]`.
  // Note: declared `val` so that the implicit picked up by LifecycleBifunctorized factories is the
  // same dictionary as the one users would see. Not `implicitly[...]` to avoid the
  // self-cycle warning Scala emits when an implicit val tries to resolve via implicit search.
  private val F: IO2[Bifunctorized.NoOp[ZBIO, +_, +_]] = izumi.functional.bio.Bifunctorized.bifunctorIsAlreadyBifunctor[ZBIO]
  private implicit def fImplicit: IO2[Bifunctorized.NoOp[ZBIO, +_, +_]] = F

  // The underlying ZIO IO2 — used to construct effect values inside use-blocks.
  private val Z: IO2[ZBIO] = implicitly

  private val runner: UnsafeRun2[ZBIO] = UnsafeRun2.createZIO[Any]()

  private def runProgram[A](z: ZThrow[A]): A = {
    runner.unsafeRunSync(z) match {
      case Exit.Success(v) => v
      case other => throw new AssertionError(s"expected Success, got $other")
    }
  }

  private def runExit[A](z: ZThrow[A]): Exit[Throwable, A] = runner.unsafeRunSync(z)

  "LifecycleBifunctorized" should {

    "make(acquire)(release) round-trips through .use" in {
      val acquired = new AtomicInteger(0)
      val released = new AtomicInteger(0)
      val lifecycle: Lifecycle[ZThrow, Int] =
        LifecycleBifunctorized.make[ZBIO, Int](
          acquire = F.sync { acquired.incrementAndGet(); 42 }
        )(release = _ => F.sync { released.incrementAndGet(); () })

      val program: ZThrow[Int] = lifecycle.use(Z.pure(_))
      assert(runProgram(program) == 42)
      assert(acquired.get() == 1, s"acquire ran ${acquired.get()} times")
      assert(released.get() == 1, s"release ran ${released.get()} times")
    }

    "pure(42).use(F.pure) yields 42" in {
      val lifecycle: Lifecycle[ZThrow, Int] = LifecycleBifunctorized.pure[ZBIO, Int](42)
      assert(runProgram(lifecycle.use(Z.pure(_))) == 42)
    }

    "liftF(F.pure(42)).use yields 42" in {
      val lifecycle: Lifecycle[ZThrow, Int] = LifecycleBifunctorized.liftF[ZBIO, Int](F.pure(42))
      assert(runProgram(lifecycle.use(Z.pure(_))) == 42)
    }

    "release fires when the use-block fails" in {
      val released = new AtomicInteger(0)
      val boom = new RuntimeException("use-block boom")
      val lifecycle: Lifecycle[ZThrow, Int] =
        LifecycleBifunctorized.make[ZBIO, Int](
          acquire = F.pure(1)
        )(release = _ => F.sync { released.incrementAndGet(); () })

      val program: ZThrow[Int] = lifecycle.use(_ => Z.fail(boom))
      runExit(program) match {
        case Exit.Error(t, _) => assert(t eq boom, s"expected $boom, got $t")
        case other => fail(s"expected Error($boom), got $other")
      }
      assert(released.get() == 1, s"release should fire on failure; ran ${released.get()} times")
    }

    "suspend evaluates lazily — its by-name argument is not invoked at construction" in {
      val evaluated = new AtomicInteger(0)
      val lifecycle: Lifecycle[ZThrow, Int] = LifecycleBifunctorized.suspend[ZBIO, Int] {
        evaluated.incrementAndGet()
        F.pure(LifecycleBifunctorized.pure[ZBIO, Int](99))
      }
      // Construction of the Lifecycle should NOT have evaluated the by-name suspend block.
      assert(evaluated.get() == 0, s"suspend evaluated eagerly: ${evaluated.get()}")

      val program: ZThrow[Int] = lifecycle.use(Z.pure(_))
      assert(runProgram(program) == 99)
      assert(evaluated.get() == 1, s"suspend should run once during execution: ${evaluated.get()}")
    }

    "fail produces a failed Lifecycle that surfaces the throwable on .use" in {
      val boom = new RuntimeException("lifecycle fail")
      val lifecycle: Lifecycle[ZThrow, Int] = LifecycleBifunctorized.fail[ZBIO, Int](boom)
      val program: ZThrow[Int] = lifecycle.use(Z.pure(_))
      runExit(program) match {
        case Exit.Error(t, _) => assert(t eq boom, s"expected $boom, got $t")
        case other => fail(s"expected Error($boom), got $other")
      }
    }

    "unit produces a Lifecycle that resolves to ()" in {
      val lifecycle: Lifecycle[ZThrow, Unit] = LifecycleBifunctorized.unit[ZBIO]
      val result: Unit = runProgram(lifecycle.use(_ => Z.pure(())))
      // Asserting on `result` itself would trip 2.13's `Unit == Unit` -Wfatal-warning;
      // the absence of an exception above is the actual signal we want.
      val _ = result
      succeed
    }

  }

}
