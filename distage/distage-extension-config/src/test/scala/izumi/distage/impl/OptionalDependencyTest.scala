package izumi.distage.impl

import java.io.ByteArrayInputStream

import distage.DIResource
import izumi.distage.model.definition.ModuleDef
import izumi.distage.model.effect.{DIEffect, LowPriorityDIEffectInstances}
import izumi.functional.bio.{BIO, BIO3, BIOAsync, BIOMonad, BIOMonadAsk, BIOPrimitives, BIORef3, BIOTemporal, F}
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

    type EitherFn[-R, +E, +A] = R => Either[E, A]
    implicit val bioEitherFn: BIO3[EitherFn] = null
    try threeTo2[EitherFn]
    catch { case _: NullPointerException => }

    def threeTo2[FR[-_, +_, +_]](implicit FR: BIO3[FR]): FR[Any, Nothing, Unit] = {
      val F: BIO[FR[Any, +?, +?]] = implicitly // must use `BIOConvert3To2` instance to convert FR -> F
      F.unit
    }

    try DIEffect.fromBIO(null)
    catch { case _: NullPointerException => }
    try BIO[Either, Unit](())(null)
    catch { case _: NullPointerException => }

    And("Methods that mention cats/ZIO types directly cannot be referred")
//    assertDoesNotCompile("DIEffect.fromBIO(BIO.BIOZio)")
//    assertDoesNotCompile("DIResource.fromCats(null)")
//    assertDoesNotCompile("DIResource.providerFromCats(null)(null)")
    BIOAsync[Either](null)

    DIResource.makePair(Some((1, Some(()))))

    And("Can search for BIO/BIOAsync")
    def optSearch[A >: Null](implicit a: A = null) = a
    optSearch[BIOTemporal[Either]]
    optSearch[BIOAsync[Either]]
    optSearch[BIO[Either]]

    And("`No More Orphans` type provider object is accessible")
    LowPriorityDIEffectInstances._Sync.hashCode()
    And("`No More Orphans` type provider implicit is not found when cats is not on the classpath")
    assertTypeError("""
         def y[R[_[_]]: LowPriorityDIEffectInstances._Sync]() = ()
         y()
      """)

    And("Methods that use `No More Orphans` trick can be called with nulls, but will error")
    intercept[NoClassDefFoundError] {
      DIEffect.fromCatsEffect[Option, DIResource[?[_], Int]](null, null)
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
