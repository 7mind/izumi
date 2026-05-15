package izumi.distage.modules.support

import cats.Parallel
import cats.effect.kernel.Async
import cats.effect.std.Dispatcher
import izumi.distage.model.definition.ModuleDef
import izumi.distage.modules.typeclass.CatsEffectInstancesModule
import izumi.functional.bio.*
import izumi.functional.bio.impl.CatsToBIO
import izumi.reflect.{TagK, TagKK}

object AnyCatsEffectSupportModule {
  /**
    * Any `cats-effect` effect type support for `distage` resources, effects, roles & tests.
    *
    * For all `F[_]` with available `make[Async[F]]`, `make[Parallel[F]]` and `make[Dispatcher[F]]` bindings.
    *
    *  - Adds [[izumi.functional.bio]] bifunctor BIO instances on `Bifunctorized[F, +_, +_]`
    *  - Adds `cats-effect` typeclass instances for `F[_]`
    *
    * Depends on `make[Async[F]]`, `make[Parallel[F]]`, `make[Dispatcher[F]]`.
    */
  def usingAsyncParallelDispatcher[F[_]: TagK]: ModuleDef = new ModuleDef {
    include(AnyCatsEffectSupportModule.usingAsyncParallel[F])
  }

  def usingAsyncParallel[F[_]: TagK]: ModuleDef = new ModuleDef {
    include(CatsEffectInstancesModule.usingAsync[F])

    addImplicit[TagK[F]]
    addImplicit[TagKK[Bifunctorized[F, +_, +_]]]

    // The bifunctor BIO dictionary on Bifunctorized[F, +_, +_] is synthesized from Async[F] + TagK[F]
    // via CatsToBIO.asyncToBIO (M1 PR-04).
    make[IO2[Bifunctorized[F, +_, +_]]].from {
      (F: Async[F]) =>
        CatsToBIO.asyncToBIO[F](using F, TagK[F])
    }
    make[Primitives2[Bifunctorized[F, +_, +_]]].from {
      (F: Async[F]) =>
        CatsToBIO.asyncToBIO[F](using F, TagK[F])
    }
    make[Async2[Bifunctorized[F, +_, +_]]].from {
      (F: Async[F]) =>
        CatsToBIO.asyncToBIO[F](using F, TagK[F])
    }
    make[Temporal2[Bifunctorized[F, +_, +_]]].from {
      (F: Async[F]) =>
        CatsToBIO.asyncToBIO[F](using F, TagK[F])
    }
  }

  /**
    * Make [[AnyCatsEffectSupportModule.usingAsyncParallelDispatcher]], binding the required dependencies in place to values from implicit scope
    */
  def withImplicits[F[_]: TagK: Async: Parallel: Dispatcher]: ModuleDef = new ModuleDef {
    include(AnyCatsEffectSupportModule.usingAsyncParallelDispatcher[F])

    addImplicit[Async[F]]
    addImplicit[Parallel[F]]
    addImplicit[Dispatcher[F]]
  }

  @deprecated("renamed to usingDependencies", "1.3")
  @inline def apply[F[_]: TagK]: ModuleDef = usingAsyncParallel[F]
}
