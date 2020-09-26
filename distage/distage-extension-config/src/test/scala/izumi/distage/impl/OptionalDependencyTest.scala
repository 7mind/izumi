package izumi.distage.impl

import java.io.ByteArrayInputStream

import distage.DIResource
import izumi.distage.model.definition.ModuleDef
import izumi.distage.model.effect.DIEffect
import izumi.distage.modules.DefaultModule
import izumi.functional.bio.{BIO, BIO3, BIOApplicative, BIOApplicativeError, BIOArrow, BIOArrowChoice, BIOAsk, BIOAsync, BIOBifunctor, BIOBracket, BIOConcurrent, BIOError, BIOFork, BIOFunctor, BIOGuarantee, BIOLocal, BIOMonad, BIOMonadAsk, BIOPanic, BIOParallel, BIOPrimitives, BIOProfunctor, BIORef3, BIOTemporal, BlockingIO, F}
import izumi.fundamentals.platform.functional.{Identity, Identity2, Identity3}
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec

class OptionalDependencyTest extends AnyWordSpec with GivenWhenThen {

  "test 2" in {
    // update ref from the environment and return result
    def adderEnv[F[-_, +_, +_]: BIOMonadAsk](i: Int): F[BIORef3[F, Int], Nothing, Int] = {
      F.access {
        ref =>
          for {
            _ <- ref.update(_ + i)
            res <- ref.get
          } yield res
      }
    }

    locally {
      implicit val ask: BIOMonadAsk[Identity3] = null
      intercept[NullPointerException](adderEnv[Identity3](0))
    }
  }

  "test 1" in {
    def adder[F[+_, +_]: BIOMonad: BIOPrimitives](i: Int): F[Nothing, Int] = {
      F.mkRef(0)
        .flatMap(ref => ref.update(_ + i) *> ref.get)
    }

    locally {
      implicit val bioMonad: BIOMonad[Identity2] = null
      implicit val primitives: BIOPrimitives[Identity2] = null
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

  "Using DIResource & DIEffect objects succeeds event if there's no cats/zio/monix on the classpath" in {
    When("There's no cats/zio/monix on classpath")
    assertCompiles("import scala._")
    assertDoesNotCompile("import cats._")
    assertDoesNotCompile("import zio._")
    assertDoesNotCompile("import monix._")

    Then("DIEffect methods can be called")
    def x[F[_]: DIEffect] = DIEffect[F].pure(1)

    And("DIEffect in DIEffect object resolve")
    assert(x[Identity] == 1)

    trait SomeBIO[+E, +A]
    type SomeBIO3[-R, +E, +A] = R => SomeBIO[E, A]

    def threeTo2[FR[-_, +_, +_]](implicit FR: BIO3[FR]): FR[Any, Nothing, Unit] = {
      val F: BIO[FR[Any, +?, +?]] = implicitly // must use `BIOConvert3To2` instance to convert FR -> F
      F.unit
    }

    def optSearch[A](implicit a: A = null.asInstanceOf[A]) = a
    final class optSearch1[C[_[_]]] { def find[F[_]](implicit a: C[F] = null.asInstanceOf[C[F]]): C[F] = a }

    locally {
      implicit val BIO3SomeBIO3: BIO3[SomeBIO3] = null
      try threeTo2[SomeBIO3]
      catch {
        case _: NullPointerException =>
      }
    }

    assert(new optSearch1[DIEffect].find == DIEffect.diEffectIdentity)

    try DIEffect.fromBIO(null)
    catch { case _: NullPointerException => }
    try BIO[SomeBIO, Unit](())(null)
    catch { case _: NullPointerException => }

    And("Methods that mention cats/ZIO types directly cannot be referred")
//    assertDoesNotCompile("DIEffect.fromBIO(BIO.BIOZio)")
//    assertDoesNotCompile("DIResource.fromCats(null)")
//    assertDoesNotCompile("DIResource.providerFromCats(null)(null)")
    BIOAsync[SomeBIO](null)

    DIResource.makePair(Some((1, Some(()))))

    And("Can search for all hierarchy classes")
    optSearch[BIOFunctor[SomeBIO]]
    optSearch[BIOApplicative[SomeBIO]]
    optSearch[BIOMonad[SomeBIO]]
    optSearch[BIOBifunctor[SomeBIO]]
    optSearch[BIOGuarantee[SomeBIO]]
    optSearch[BIOApplicativeError[SomeBIO]]
    optSearch[BIOError[SomeBIO]]
    optSearch[BIOBracket[SomeBIO]]
    optSearch[BIOPanic[SomeBIO]]
    optSearch[BIOParallel[SomeBIO]]
    optSearch[BIO[SomeBIO]]
    optSearch[BIOAsync[SomeBIO]]
    optSearch[BIOTemporal[SomeBIO]]
    optSearch[BIOConcurrent[SomeBIO]]
    optSearch[BIOAsk[SomeBIO3]]
    optSearch[BIOMonadAsk[SomeBIO3]]
    optSearch[BIOProfunctor[SomeBIO3]]
    optSearch[BIOArrow[SomeBIO3]]
    optSearch[BIOArrowChoice[SomeBIO3]]
    optSearch[BIOLocal[SomeBIO3]]

    optSearch[BIOFork[SomeBIO]]
    optSearch[BIOPrimitives[SomeBIO]]
    optSearch[BlockingIO[SomeBIO]]

    And("`No More Orphans` type provider object is accessible")
    izumi.fundamentals.orphans.`cats.effect.Sync`.hashCode()
    And("`No More Orphans` type provider implicit is not found when cats is not on the classpath")
    assertTypeError("""
         def y[R[_[_]]: LowPriorityDIEffectInstances._Sync]() = ()
         y()
      """)

    And("Methods that use `No More Orphans` trick can be called with nulls, but will error")
    intercept[NoClassDefFoundError] {
      DIEffect.fromCats[Option, DIResource[?[_], Int]](null, null)
    }

    And("Methods that mention cats types only in generics will error on call")
//    assertDoesNotCompile("DIResource.providerFromCatsProvider[Identity, Int](() => null)")

    Then("DIResource.use syntax works")
    var open = false
    val resource = DIResource.makeSimple {
      open = true
      new ByteArrayInputStream(Array())
    } {
      i => open = false; i.close()
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

//    Then("DIResource.toCats doesn't work")
//    assertDoesNotCompile("resource.toCats")
  }

}
