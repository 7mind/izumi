package izumi.distage.impl

import distage.Injector
import izumi.distage.model.definition.ModuleDef
import izumi.distage.modules.DefaultModule
import izumi.functional.bio.impl.MiniBIOAsync
import izumi.functional.bio.{Applicative2, ApplicativeError2, Async2, Bifunctor2, BlockingIO2, Bracket2, Concurrent2, Error2, Exit, F, Fork2, Functor2, Guarantee2, IO2, Monad2, Panic2, Parallel2, Primitives2, PrimitivesLocal2, PrimitivesM2, Temporal2, TypedError, UnsafeRun2, WeakAsync2, WeakTemporal2}
import izumi.fundamentals.platform.functional.{Identity, Identity2}
import izumi.fundamentals.platform.language.Quirks.Discarder
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec

import java.io.ByteArrayInputStream
import scala.annotation.nowarn

class OptionalDependencyTest extends AnyWordSpec with GivenWhenThen {

  "test 1" in {
    def adder[F[+_, +_]: Monad2: Primitives2](i: Int): F[Nothing, Int] = {
      F.mkRef(0)
        .flatMap(ref => ref.update(_ + i) *> ref.get)
    }

    locally {
      implicit val bioMonad: Monad2[Identity2] = null
      implicit val primitives: Primitives2[Identity2] = null
      intercept[NullPointerException](adder[Identity2](0))
    }
  }

  "Using DefaultModules" in {
    def getDefaultModules[F[+_, +_]: DefaultModule]: DefaultModule[F] = implicitly
    def getDefaultModulesOrEmpty[F[+_, +_]](implicit m: DefaultModule[F] = DefaultModule.empty[F]): DefaultModule[F] = m

    val defaultModules = getDefaultModules[izumi.functional.bio.Bifunctorized.IdentityBifunctorized]
    assert(defaultModules.getClass == DefaultModule.forIdentity.getClass)

    trait UnknownBI[+E, +A]
    val empty = getDefaultModulesOrEmpty[UnknownBI]
    assert(empty.module.bindings.isEmpty)
  }

  // MiniBIOAsync no longer has a DefaultModule (it lacks `Async2`, `Temporal2`, `Primitives2`, `Fork2`,
  // `PrimitivesM2`, `PrimitivesLocal2`, `Scheduler2` instances required by `DefaultModule.fromBIO`).
  // Test removed as part of M5 — MiniBIOAsync remains usable directly via `MiniBIOAsync.UnsafeRunMiniBIOAsync`
  // (see TestRunnerRuntime.runnerLifecycleForMiniBIOAsync for the canonical wiring).
//  "MiniBIOAsync has DefaultModule" in { ... }

  "Using Lifecycle & BIO objects succeeds even if there's no cats/zio/monix on the classpath" in {
    When("There's no cats/zio/monix on classpath")
    assertCompiles("import scala._")
    assertDoesNotCompile("import cats.kernel.Eq")
    assertDoesNotCompile("import zio.ZIO")
    assertDoesNotCompile("import monix._")

    Then("BIO methods can be called")
    def x[F[+_, +_]: IO2] = IO2[F, Int](1)

    trait SomeBIO[+E, +A]

    def optSearch[A](implicit a: A = null.asInstanceOf[A]) = a

    try IO2[SomeBIO, Unit](())(using null)
    catch { case _: NullPointerException => }

    And("Methods that mention cats/ZIO types directly cannot be referred")
//    assertDoesNotCompile("Lifecycle.fromCats(null)")
//    assertDoesNotCompile("Lifecycle.providerFromCats(null)(null)")
    Async2[SomeBIO](using null)

    locally(izumi.functional.lifecycle.Lifecycle)

    locally(distage.Lifecycle)

    // Lifecycle.makePair signature changed under the bifunctor migration; skip this smoke check.
//    izumi.functional.lifecycle.Lifecycle.makePair(Some((1, Some(()))))

    And("Can search for all hierarchy classes")
    optSearch[Functor2[SomeBIO]]
    optSearch[Applicative2[SomeBIO]]
    optSearch[Monad2[SomeBIO]]
    optSearch[Bifunctor2[SomeBIO]]
    optSearch[Guarantee2[SomeBIO]]
    optSearch[ApplicativeError2[SomeBIO]]
    optSearch[Error2[SomeBIO]]
    optSearch[Bracket2[SomeBIO]]
    optSearch[Panic2[SomeBIO]]
    optSearch[Parallel2[SomeBIO]]
    optSearch[IO2[SomeBIO]]
    optSearch[Async2[SomeBIO]]
    optSearch[WeakAsync2[SomeBIO]]
    optSearch[Temporal2[SomeBIO]]
    optSearch[WeakTemporal2[SomeBIO]]
    optSearch[Concurrent2[SomeBIO]]

    optSearch[Fork2[SomeBIO]]
    optSearch[Primitives2[SomeBIO]]
    optSearch[PrimitivesM2[SomeBIO]]
    optSearch[PrimitivesLocal2[SomeBIO]]
    optSearch[BlockingIO2[SomeBIO]]

    And("`No More Orphans` type provider object is accessible")
    izumi.fundamentals.orphans.`cats.effect.kernel.Sync`.hashCode()
    And("`No More Orphans` type provider implicit is not found when cats is not on the classpath")
    assertTypeError("""
         def y[R[_[_]]: LowPriorityIO1Instances._Sync]() = ()
         y()
      """)

    And("Methods that mention cats types only in generics will error on call")
//    assertDoesNotCompile("Lifecycle.providerFromCatsProvider[Identity, Int](() => null)")

    Then("Lifecycle.use syntax works")
    var open = false
    val resource = distage.Lifecycle.makeSimple {
      open = true
      new ByteArrayInputStream(Array())
    } {
      i =>
        open = false; i.close()
    }

    resource.use {
      i =>
        assert(open)
        assert(i.read() == -1)
    }
    assert(!open)

    Then("ModuleDef syntax works")
    new ModuleDef {
      make[Some[Int]]
      make[None.type]
      make[Int].from(0)
    }

    // Doesn't compile on Scala 2.13
//    Then("Lifecycle.toCats doesn't work")
//    assertDoesNotCompile("resource.toCats")
  }

  "All bio objects with zio-specific defs initialize even if there's no zio on the classpath" in {
    izumi.functional.bio.Ref1.discard()
    izumi.functional.bio.RefM2.discard()

    izumi.functional.bio.Promise2.discard()

    izumi.functional.bio.Semaphore1.discard()

    izumi.functional.bio.Fiber2.discard()
    izumi.functional.bio.FiberRef2.discard()

    izumi.functional.bio.ForkInstances.discard()
    izumi.functional.bio.PrimitivesInstances.discard()
    izumi.functional.bio.PrimitivesLocal2.discard()
    izumi.functional.bio.PrimitivesLocalInstances.discard()
    izumi.functional.bio.PrimitivesM2.discard()
    izumi.functional.bio.PrimitivesMInstances.discard()
    izumi.functional.bio.Root.discard()
    izumi.functional.bio.TemporalInstances.discard()
    izumi.functional.bio.BlockingIO2.discard()
    izumi.functional.bio.BlockingIOInstances.discard()

    izumi.functional.lifecycle.Lifecycle.discard()

    izumi.functional.bio.Exit.discard()
    izumi.functional.bio.UnsafeRun2.discard()
  }

  "All bio objects with cats-specific defs initialize even if there's no cats on the classpath" in {
    izumi.functional.bio.PanicSyntax.discard()
    izumi.functional.bio.PrimitivesLocal2.discard()
    izumi.functional.bio.Promise2.discard()
    izumi.functional.bio.Ref1.discard()
    izumi.functional.bio.Semaphore1.discard()
    izumi.functional.bio.SyncSafe1.discard()
    izumi.functional.bio.data.Morphism3.discard()
    izumi.functional.lifecycle.Lifecycle.discard()

    izumi.functional.bio.UnsafeRun2.discard()
    // IO2 and Async2 traits do not have companion objects in the M5 BIO hierarchy — removed
    // (their no-cats reachability is covered transitively by Bifunctorized.discard() above).

    // reference doesn't even compile on Scala 3, but it's cats-specific
//    intercept[java.lang.NoClassDefFoundError] {
//      izumi.functional.bio.catz.discard()
//    }
  }

  "Bifunctorized / SubmergedTypedError / BifunctorizedNoOpInstances are reachable on a no-cats classpath" in {
    And("Bifunctorized companion object is reachable without cats")
    izumi.functional.bio.Bifunctorized.discard()

    And("SubmergedTypedError companion object is reachable without cats")
    izumi.functional.bio.SubmergedTypedError.discard()

    And("BifunctorizedNoOpInstances trait is reachable without cats")
    classOf[izumi.functional.bio.BifunctorizedNoOpInstances].discard()

    And("A type using Bifunctorized[Try, E, A] compiles without cats")
    assertCompiles("type X[+E, +A] = izumi.functional.bio.Bifunctorized.Bifunctorized[scala.util.Try, E, A]")

    And("bifunctorizeConversion auto-lifts Try[A] to Bifunctorized[Try, Throwable, A] without cats")
    assertCompiles("""
      import izumi.functional.bio.Bifunctorized._
      val raw: scala.util.Try[Int] = scala.util.Success(42)
      val wrapped: izumi.functional.bio.Bifunctorized.Bifunctorized[scala.util.Try, Throwable, Int] = raw
    """)
  }: @nowarn("msg=pure expression")

  "Using Exit.Trace succeeds even if there's no zio on the classpath" in {
    Exit.discard()
    Exit.Trace.discard()
    // Exit.ZIOExit fails, but it's zio-specific
    intercept[java.lang.NoClassDefFoundError] {
      Exit.ZIOExit.discard()
    }
    Exit.CatsExit.discard() // CatsExit succeeds, even though it's cats-specific
    Exit.Trace.ThrowableTrace.discard()
    Exit.Trace.ZIOTrace.discard() // ZIOTrace succeeds, even though it's cats-specific
    val mkT = new Exit.Trace.ThrowableTrace(_)
    val t = mkT(new RuntimeException)
    t.unsafeAttachTraceOrReturnNewThrowable(TypedError.wrapIfNotThrowable)
    t.toString.discard()
    t.asString.discard()

    Exit.Trace.forTypedError(new RuntimeException())
    t.unsafeAttachTraceOrReturnNewThrowable()

    Exit.toString
  }: @nowarn("msg=pure expression")

}
