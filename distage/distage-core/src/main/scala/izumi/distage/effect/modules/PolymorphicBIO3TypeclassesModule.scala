package izumi.distage.effect.modules

import izumi.distage.model.definition.ModuleDef
import izumi.functional.bio._
import izumi.reflect.TagK3

/**
  * Adds `bio` typeclass instances for any effect type `F[+_, +_]` with an available `make[BIOAsync3[F]` binding
  *
  * Depends on `make[BIOAsync3[F]]`
  */
class PolymorphicBIO3TypeclassesModule[F[-_, +_, +_]: TagK3] extends ModuleDef {
  include(PolymorphicBIOTypeclassesModule[F[Any, +?, +?]])

  make[BIOFunctor3[F]].using[BIOAsync3[F]]
  make[BIOBifunctor3[F]].using[BIOAsync3[F]]
  make[BIOApplicative3[F]].using[BIOAsync3[F]]
  make[BIOGuarantee3[F]].using[BIOAsync3[F]]
  make[BIOApplicativeError3[F]].using[BIOAsync3[F]]
  make[BIOMonad3[F]].using[BIOAsync3[F]]
  make[BIOError3[F]].using[BIOAsync3[F]]
  make[BIOBracket3[F]].using[BIOAsync3[F]]
  make[BIOPanic3[F]].using[BIOAsync3[F]]
  make[BIO3[F]].using[BIOAsync3[F]]
  make[BIOParallel3[F]].using[BIOAsync3[F]]
  make[BIOConcurrent3[F]].using[BIOAsync3[F]]
}

object PolymorphicBIO3TypeclassesModule {
  @inline def apply[F[-_, +_, +_]: TagK3]: PolymorphicBIO3TypeclassesModule[F] = new PolymorphicBIO3TypeclassesModule

  /**
    * Make [[PolymorphicBIO3TypeclassesModule]], binding the required dependencies in place to values from implicit scope
    *
    * `make[BIOTemporal3[F]]`, `make[BIORunner3[F]]` `make[BIOFork3[F]]` and `make[BIOPrimitives3[F]]` are not required by [[PolymorphicBIO3TypeclassesModule]]
    * but are added for completeness
    */
  def withImplicits[F[-_, +_, +_]: TagK3: BIOAsync3: BIOTemporal3: BIORunner3: BIOFork3: BIOPrimitives3]: ModuleDef = new ModuleDef {
    include(PolymorphicBIO3TypeclassesModule[F])

    addImplicit[BIOAsync3[F]]
    addImplicit[BIOFork3[F]]
    addImplicit[BIOTemporal3[F]]
    addImplicit[BIOPrimitives3[F]]
    addImplicit[BIORunner3[F]]
  }
}
