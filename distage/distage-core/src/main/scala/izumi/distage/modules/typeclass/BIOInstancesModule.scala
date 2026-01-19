package izumi.distage.modules.typeclass

import izumi.distage.model.definition.ModuleDef
import izumi.functional.bio.*
import izumi.functional.bio.retry.Scheduler2
import izumi.reflect.TagKK

object BIOInstancesModule {

  /**
    * Adds `bio` typeclass instances aliases for any effect type `F[+_, +_]` using aavailable
    * `make[Async2[F]]` and `make[Temporal2[F]]` bindings
    *
    * Depends on `make[Async2[F]]` and `make[Temporal2[F]]`
    *
    * @see [[izumi.functional.bio]]
    */
  def usingAsyncTemporal[F[+_, +_]: TagKK]: ModuleDef = new ModuleDef {
    make[Functor2[F]].using[Async2[F]]
    make[Bifunctor2[F]].using[Async2[F]]
    make[Applicative2[F]].using[Async2[F]]
    make[Guarantee2[F]].using[Async2[F]]
    make[ApplicativeError2[F]].using[Async2[F]]
    make[Monad2[F]].using[Async2[F]]
    make[Error2[F]].using[Async2[F]]
    make[Bracket2[F]].using[Async2[F]]
    make[Panic2[F]].using[Async2[F]]
    make[IO2[F]].using[Async2[F]]
    make[Parallel2[F]].using[Async2[F]]
    make[Concurrent2[F]].using[Async2[F]]
    make[WeakAsync2[F]].using[Async2[F]]

    make[WeakTemporal2[F]].using[Temporal2[F]]
  }

  /**
    * Make [[BIOInstancesModule.usingAsyncTemporal]], binding the required dependencies in place to values from implicit scope
    */
  def usingAsyncTemporalImplicits[F[+_, +_]: TagKK: Async2: Temporal2]: ModuleDef = new ModuleDef {
    include(BIOInstancesModule.usingAsyncTemporal[F])

    addImplicit[Async2[F]]
    addImplicit[Temporal2[F]]
  }

  /** Bind BIO aux algebras, those outside of the main hierarchy from implicits */
  def auxAlgebrasImplicits[F[+_, +_]: TagKK: UnsafeRun2: Fork2: BlockingIO2: Primitives2: PrimitivesM2: PrimitivesLocal2: Scheduler2]: ModuleDef =
    new ModuleDef {
      addImplicit[UnsafeRun2[F]]
      addImplicit[Fork2[F]]
      addImplicit[BlockingIO2[F]]
      addImplicit[Primitives2[F]]
      addImplicit[PrimitivesM2[F]]
      addImplicit[PrimitivesLocal2[F]]
      addImplicit[Scheduler2[F]]
    }

  /**
    * Make [[BIOInstancesModule.usingAsyncTemporalImplicits]], binding the required dependencies in place to values from implicit scope
    *
    * And bind aux algebras from implicit scope via [[BIOInstancesModule.auxAlgebrasImplicits]]
    */
  def usingAsyncTemporalAuxAlgebrasImplicits[
    F[+_, +_]: TagKK: Async2: Temporal2: UnsafeRun2: Fork2: BlockingIO2: Primitives2: PrimitivesM2: PrimitivesLocal2: Scheduler2
  ]: ModuleDef =
    new ModuleDef {
      include(BIOInstancesModule.usingAsyncTemporalImplicits[F])
      include(BIOInstancesModule.auxAlgebrasImplicits[F])
    }
}
