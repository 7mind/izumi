package izumi.distage.model

import izumi.distage.model.definition.ModuleDef
import izumi.distage.model.plan.Roots
import izumi.distage.modules.DefaultModule
import izumi.functional.bio.IO2
import org.scalatest.wordspec.AnyWordSpec
import zio.{Task, Unsafe, ZIO}

final class BifunctorizedInjectorTest extends AnyWordSpec {

  private def unsafeRun[E, A](eff: => ZIO[Any, E, A]): A =
    Unsafe.unsafe(implicit u => zio.Runtime.default.unsafe.run(eff).getOrThrowFiberFailure())

  // pull in the implicit DefaultModule[ZIO[Any, Throwable, _]] = forZIO[ZIO, Any] for tests
  // that don't need `forZIOPlusCats` (which itself resolves implicitly when cats-effect is on
  // the classpath; we make the simpler ZIO-only DefaultModule explicit to keep the test focused
  // on the BIO-constrained factory shape).
  private implicit val defaultModuleZIO: DefaultModule[ZIO[Any, Throwable, _]] = DefaultModule.forZIO[ZIO, Any]

  "BifunctorizedInjector" should {

    "construct an injector for ZIO[Any, +_, +_] and resolve a tiny module" in {
      final class Greeter { def greet: String = "hello" }

      val module = new ModuleDef {
        make[Greeter]
      }

      val injector = BifunctorizedInjector[ZIO[Any, +_, +_]]()

      val result: Task[String] = injector.produceRun(module) {
        (g: Greeter) => ZIO.succeed(g.greet)
      }
      assert(unsafeRun(result) == "hello")
    }

    "inherit from a parent locator" in {
      final class Parent(val name: String)
      final class Child(val parent: Parent)

      val parentInjector = BifunctorizedInjector[ZIO[Any, +_, +_]]()
      val parentLocator = unsafeRun(
        parentInjector
          .produce(new ModuleDef { make[Parent].from(new Parent("p")) }, Roots.Everything)
          .use(ZIO.succeed(_))
      )

      val childInjector = BifunctorizedInjector.inherit[ZIO[Any, +_, +_]](parentLocator)
      val name = unsafeRun(
        childInjector.produceRun(new ModuleDef { make[Child] }) {
          (c: Child) => ZIO.succeed(c.parent.name)
        }
      )
      assert(name == "p")
    }

    "produce an Injector[ZIO[Any, Throwable, _]] (type check)" in {
      val injector: Injector[ZIO[Any, Throwable, _]] = BifunctorizedInjector[ZIO[Any, +_, +_]]()
      assert(injector ne null)
    }

    "consume an IO2 instance from the user's implicit scope" in {
      // generic helper: any bifunctor with an IO2 derived for its NoOp wrapper builds an injector.
      def mkInjector[F[+_, +_]](
        implicit F: izumi.functional.bio.IO2[izumi.functional.bio.Bifunctorized.NoOp[F, +_, +_]],
        tag: izumi.reflect.TagKK[F],
        dm: DefaultModule[F[Throwable, _]],
      ): Injector[F[Throwable, _]] = BifunctorizedInjector[F]()

      // sanity: IO2[ZIO[Any, +_, +_]] is on classpath (ZIOSupportModule region)
      val _ = implicitly[IO2[ZIO[Any, +_, +_]]]
      val injector: Injector[ZIO[Any, Throwable, _]] = mkInjector[ZIO[Any, +_, +_]]
      assert(injector ne null)
    }

  }

}
