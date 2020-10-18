package izumi.distage.modules.typeclass

import izumi.distage.model.definition.ModuleDef
import izumi.functional.bio._
import izumi.reflect.TagK3

/**
  * Adds `bio` typeclass instances for any effect type `F[-_, +_, +_]` with an available `make[Async3[F]` binding
  *
  * Depends on `make[Async3[F]]` & `make[Local3[F]]`
  *
  * Note: doesn't add bifunctor variants from [[BIOInstancesModule]]
  */
class BIO3InstancesModule[F[-_, +_, +_]: TagK3] extends ModuleDef {
  make[BIOFunctor3[F]].using[Async3[F]]
  make[BIOBifunctor3[F]].using[Async3[F]]
  make[Applicative3[F]].using[Async3[F]]
  make[BIOGuarantee3[F]].using[Async3[F]]
  make[ApplicativeError3[F]].using[Async3[F]]
  make[BIOMonad3[F]].using[Async3[F]]
  make[BIOError3[F]].using[Async3[F]]
  make[BIOBracket3[F]].using[Async3[F]]
  make[BIOPanic3[F]].using[Async3[F]]
  make[BIO3[F]].using[Async3[F]]
  make[BIOParallel3[F]].using[Async3[F]]
  make[BIOConcurrent3[F]].using[Async3[F]]

  make[BIOAsk[F]].using[Local3[F]]
  make[MonadAsk3[F]].using[Local3[F]]
  make[BIOProfunctor[F]].using[Local3[F]]
  make[BIOArrow[F]].using[Local3[F]]
}

object BIO3InstancesModule {
  @inline def apply[F[-_, +_, +_]: TagK3]: BIO3InstancesModule[F] = new BIO3InstancesModule

  /**
    * Make [[BIO3InstancesModule]], binding the required dependencies in place to values from implicit scope
    *
    * `make[Temporal3[F]]`, `make[UnsafeRun3[F]]` `make[Fork3[F]]` and `make[Primitives3[F]]` are not required by [[BIO3InstancesModule]]
    * but are added for completeness
    */
  def withImplicits[F[-_, +_, +_]: TagK3: Async3: Temporal3: Local3: UnsafeRun3: Fork3: Primitives3]: ModuleDef = new ModuleDef {
    include(BIO3InstancesModule[F])

    addImplicit[Async3[F]]
    addImplicit[Temporal3[F]]
    addImplicit[Local3[F]]
    addImplicit[Fork3[F]]
    addImplicit[Primitives3[F]]
    addImplicit[UnsafeRun3[F]]
  }
}
