package izumi.distage.impl

import distage.Injector
import izumi.distage.model.definition.ModuleDef
import izumi.distage.modules.DefaultModule
import izumi.functional.bio.impl.MiniBIOAsync
import izumi.functional.bio.{Applicative2, ApplicativeError2, Async2, Bifunctor2, BlockingIO2, Bracket2, Concurrent2, Error2, Exit, F, Fork2, Functor2, Guarantee2, IO2, Monad2, Panic2, Parallel2, Primitives2, PrimitivesLocal2, PrimitivesM2, Temporal2, TypedError, WeakAsync2, WeakTemporal2}
import izumi.functional.quasi.{QuasiApplicative, QuasiFunctor, QuasiIO, QuasiIORunner, QuasiPrimitives}
import izumi.fundamentals.platform.functional.{Identity, Identity2}
import izumi.fundamentals.platform.language.IzScala
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
    def getDefaultModules[F[_]: DefaultModule]: DefaultModule[F] = implicitly
    def getDefaultModulesOrEmpty[F[_]](implicit m: DefaultModule[F] = DefaultModule.empty[F]): DefaultModule[F] = m

    val defaultModules = getDefaultModules
    assert((defaultModules: DefaultModule[Identity]).getClass == DefaultModule.forIdentity.getClass)

    val empty = getDefaultModulesOrEmpty[Option]
    assert(empty.module.bindings.isEmpty)
  }

  "MiniBIOAsync has DefaultModule" in {
    import scala.concurrent.ExecutionContext.Implicits.global

    implicitly[DefaultModule[MiniBIOAsync[Throwable, _]]]

    Injector[MiniBIOAsync[Throwable, _]]().produceRun(distage.Module.empty) {
      (runner: QuasiIORunner[MiniBIOAsync[Throwable, _]]) =>
        MiniBIOAsync.WeakAsyncForMiniBIOAsync.syncBlocking {
          runner.runBlocking(MiniBIOAsync.WeakAsyncForMiniBIOAsync.pure(()))
        }
    }
  }

  "Using Lifecycle & QuasiIO objects succeeds even if there's no cats/zio/monix on the classpath" in {
    When("There's no cats/zio/monix on classpath")
    assertCompiles("import scala._")
    assertDoesNotCompile("import cats.kernel.Eq")
    assertDoesNotCompile("import zio.ZIO")
    assertDoesNotCompile("import monix._")

    Then("QuasiIO methods can be called")
    def x[F[_]: QuasiIO] = QuasiIO[F].pure(1)

    And("QuasiIO in QuasiIO object resolve")
    assert(x[Identity] == 1)

    trait SomeBIO[+E, +A]

    def optSearch[A](implicit a: A = null.asInstanceOf[A]) = a
    final class optSearch1[C[_[_]]] { def find[F[_]](implicit a: C[F] = null.asInstanceOf[C[F]]): C[F] = a }

    assert(new optSearch1[QuasiFunctor].find == QuasiFunctor.quasiFunctorIdentity)
    assert(new optSearch1[QuasiApplicative].find == QuasiApplicative.quasiApplicativeIdentity)
    assert(new optSearch1[QuasiPrimitives].find == QuasiPrimitives.quasiPrimitivesIdentity)
    assert(new optSearch1[QuasiIO].find == QuasiIO.quasiIOIdentity)

    try QuasiIO.fromBIO(using null)
    catch { case _: NullPointerException => }
    try IO2[SomeBIO, Unit](())(using null)
    catch { case _: NullPointerException => }

    And("Methods that mention cats/ZIO types directly cannot be referred")
//    assertDoesNotCompile("QuasiIO.fromBIO(BIO.BIOZio)")
//    assertDoesNotCompile("Lifecycle.fromCats(null)")
//    assertDoesNotCompile("Lifecycle.providerFromCats(null)(null)")
    Async2[SomeBIO](using null)

    locally(izumi.functional.lifecycle.Lifecycle)

    locally(distage.Lifecycle)

    izumi.functional.lifecycle.Lifecycle.makePair(Some((1, Some(()))))

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
         def y[R[_[_]]: LowPriorityQuasiIOInstances._Sync]() = ()
         y()
      """)

    type LC[F[_]] = distage.Lifecycle[F, Int]
    And("Methods that use `No More Orphans` trick can be called with nulls, but will error")
    intercept[Throwable] {
      QuasiIO.fromCats[Option, LC](using null, null)
    } match {
      case _: NoClassDefFoundError =>
      case _: NullPointerException =>
        fail("NPE has been thrown, seems like cats are in the classpath (running under IDEA?)")
    }

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

    izumi.functional.quasi.QuasiIO.discard()
    izumi.functional.quasi.QuasiIORunner.discard()
    izumi.functional.quasi.QuasiAsync.discard()

    // fails on Scala 2, but it's cats-specific
    if (IzScala.scalaRelease.major == 2) {
      intercept[java.lang.NoClassDefFoundError] {
        new izumi.functional.bio.impl.PrimitivesFromBIOAndCats()(using null, null).discard()
      }
    } else {
//      new izumi.functional.bio.impl.PrimitivesFromBIOAndCats()(using null, null).discard()
    }
    // cats-specific, but succeeds, doesn't use arguments in constructor
    locally {
      object x { type f[+x] = Any; type g[+x] = Nothing }
      new izumi.functional.bio.impl.PrimitivesLocalFromCatsIO(null.asInstanceOf[izumi.functional.bio.data.Morphism1[x.f, x.g]])(using null).discard()
    }
    // reference doesn't even compile on Scala 3, but it's cats-specific
//    intercept[java.lang.NoClassDefFoundError] {
//      izumi.functional.bio.catz.discard()
//    }
  }

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
