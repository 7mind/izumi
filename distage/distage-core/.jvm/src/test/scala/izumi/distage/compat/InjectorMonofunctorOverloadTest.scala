package izumi.distage.compat

import cats.effect.IO
import distage.{Injector, ModuleDef}
import izumi.distage.model.plan.Roots
import izumi.functional.bio.CatsToBIOConversions.*
import org.scalatest.wordspec.AnyWordSpec

/** Tests that `Injector[F[_]]` with a monofunctor type argument compiles and runs end-to-end,
  * verifying the transparent monofunctor->bifunctor lift via the `Bifunctorize[F]` typeclass
  * (Task A of M5-fix4, `bifunctorization.md` Goal 3).
  */
final class InjectorMonofunctorOverloadTest extends AnyWordSpec with CatsIOPlatformDependentTest {

  "Injector[F[_]]" should {

    "compile and produce an Injector for cats.effect.IO via the monofunctor overload" in {
      // Compile-time check: `Injector[cats.effect.IO]()` (kind [_]) resolves to the new monofunctor
      // overload, returning `Injector[Bifunctorized[IO, +_, +_]]`.
      val module = new ModuleDef {
        make[Int].fromValue(42)
      }
      val result = catsIOUnsafeRunSync {
        Injector[cats.effect.IO]()
          .produce(module, Roots.Everything)
          .use(locator => IO(locator.get[Int]))
      }
      assert(result == 42)
    }

    "compile and produce an Injector for cats.effect.IO with bootstrap overrides" in {
      val module = new ModuleDef {
        make[String].fromValue("hello")
      }
      val result = catsIOUnsafeRunSync {
        Injector[cats.effect.IO]() // no bootstrap overrides
          .produce(module, Roots.Everything)
          .use(locator => IO(locator.get[String]))
      }
      assert(result == "hello")
    }

  }

}
