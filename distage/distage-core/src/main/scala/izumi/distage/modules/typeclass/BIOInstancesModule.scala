package izumi.distage.modules.typeclass

import izumi.distage.model.definition.ModuleDef
import izumi.functional.bio._
import izumi.reflect.TagKK

/**
  * Adds `bio` typeclass instances for any effect type `F[+_, +_]` with an available `make[Async2[F]` binding
  *
  * Depends on `make[Async2[F]]`
  */
class BIOInstancesModule[F[+_, +_]: TagKK] extends ModuleDef {
  make[BIOFunctor[F]].using[Async2[F]]
  make[BIOBifunctor[F]].using[Async2[F]]
  make[Applicative2[F]].using[Async2[F]]
  make[BIOGuarantee[F]].using[Async2[F]]
  make[ApplicativeError2[F]].using[Async2[F]]
  make[BIOMonad[F]].using[Async2[F]]
  make[BIOError[F]].using[Async2[F]]
  make[BIOBracket[F]].using[Async2[F]]
  make[BIOPanic[F]].using[Async2[F]]
  make[BIO[F]].using[Async2[F]]
  make[BIOParallel[F]].using[Async2[F]]
  make[BIOConcurrent[F]].using[Async2[F]]
}

object BIOInstancesModule {
  @inline def apply[F[+_, +_]: TagKK]: BIOInstancesModule[F] = new BIOInstancesModule

  /**
    * Make [[BIOInstancesModule]], binding the required dependencies in place to values from implicit scope
    *
    * `make[Temporal2[F]]`, `make[UnsafeRun2[F]]` `make[Fork2[F]]` and `make[Primitives2[F]]` are not required by [[BIOInstancesModule]]
    * but are added for completeness
    */
  def withImplicits[F[+_, +_]: TagKK: Async2: Temporal2: UnsafeRun2: Fork2: Primitives2]: ModuleDef = new ModuleDef {
    include(BIOInstancesModule[F])

    addImplicit[Async2[F]]
    addImplicit[Fork2[F]]
    addImplicit[Temporal2[F]]
    addImplicit[Primitives2[F]]
    addImplicit[UnsafeRun2[F]]
  }
}
