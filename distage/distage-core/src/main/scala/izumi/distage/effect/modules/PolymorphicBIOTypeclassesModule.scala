package izumi.distage.effect.modules

import izumi.distage.model.definition.ModuleDef
import izumi.functional.bio._
import izumi.reflect.TagKK

/**
  * Adds `bio` typeclass instances for any effect type `F[+_, +_]` with an available `make[BIOAsync[F]` binding
  *
  * Depends on `make[BIOAsync[F]]`
  */
class PolymorphicBIOTypeclassesModule[F[+_, +_]: TagKK] extends ModuleDef {
  make[BIOFunctor[F]].using[BIOAsync[F]]
  make[BIOBifunctor[F]].using[BIOAsync[F]]
  make[BIOApplicative[F]].using[BIOAsync[F]]
  make[BIOGuarantee[F]].using[BIOAsync[F]]
  make[BIOApplicativeError[F]].using[BIOAsync[F]]
  make[BIOMonad[F]].using[BIOAsync[F]]
  make[BIOError[F]].using[BIOAsync[F]]
  make[BIOBracket[F]].using[BIOAsync[F]]
  make[BIOPanic[F]].using[BIOAsync[F]]
  make[BIO[F]].using[BIOAsync[F]]
  make[BIOParallel[F]].using[BIOAsync[F]]
  make[BIOConcurrent[F]].using[BIOAsync[F]]
}

object PolymorphicBIOTypeclassesModule {
  @inline def apply[F[+_, +_]: TagKK]: PolymorphicBIOTypeclassesModule[F] = new PolymorphicBIOTypeclassesModule

  /**
    * Make [[PolymorphicBIOTypeclassesModule]], binding the required dependencies in place to values from implicit scope
    *
    * `make[BIOTemporal[F]]`, `make[BIORunner[F]]` `make[BIOFork[F]]` and `BIOPrimitives[F]` are not required by [[PolymorphicBIOTypeclassesModule]]
    * but are added for completeness
    */
  def withImplicits[F[+_, +_]: TagKK: BIOAsync: BIOTemporal: BIORunner: BIOFork: BIOPrimitives]: ModuleDef = new ModuleDef {
    include(PolymorphicBIOTypeclassesModule[F])

    addImplicit[BIOAsync[F]]
    addImplicit[BIOFork[F]]
    addImplicit[BIOTemporal[F]]
    addImplicit[BIOPrimitives[F]]
    addImplicit[BIORunner[F]]
  }
}
