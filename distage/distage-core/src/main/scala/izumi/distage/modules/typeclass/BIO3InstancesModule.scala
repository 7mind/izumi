package izumi.distage.modules.typeclass

import izumi.distage.model.definition.ModuleDef
import izumi.functional.bio._
import izumi.reflect.TagK3

/**
  * Adds `bio` typeclass instances for any effect type `F[-_, +_, +_]` with an available `make[BIOAsync3[F]` binding
  *
  * Depends on `make[BIOAsync3[F]]` & `make[BIOLocal[F]]`
  *
  * Note: doesn't add bifunctor variants from [[BIOInstancesModule]]
  */
class BIO3InstancesModule[F[-_, +_, +_]: TagK3] extends ModuleDef {
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

  make[BIOAsk[F]].using[BIOLocal[F]]
  make[BIOMonadAsk[F]].using[BIOLocal[F]]
  make[BIOProfunctor[F]].using[BIOLocal[F]]
  make[BIOArrow[F]].using[BIOLocal[F]]
}

object BIO3InstancesModule {
  @inline def apply[F[-_, +_, +_]: TagK3]: BIO3InstancesModule[F] = new BIO3InstancesModule

  /**
    * Make [[BIO3InstancesModule]], binding the required dependencies in place to values from implicit scope
    *
    * `make[BIOTemporal3[F]]`, `make[BIORunner3[F]]` `make[BIOFork3[F]]` and `make[BIOPrimitives3[F]]` are not required by [[BIO3InstancesModule]]
    * but are added for completeness
    */
  def withImplicits[F[-_, +_, +_]: TagK3: BIOAsync3: BIOTemporal3: BIOLocal: BIORunner3: BIOFork3: BIOPrimitives3]: ModuleDef = new ModuleDef {
    include(BIO3InstancesModule[F])

    addImplicit[BIOAsync3[F]]
    addImplicit[BIOTemporal3[F]]
    addImplicit[BIOLocal[F]]
    addImplicit[BIOFork3[F]]
    addImplicit[BIOPrimitives3[F]]
    addImplicit[BIORunner3[F]]
  }
}
