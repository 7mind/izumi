package izumi.distage.modules.support

import izumi.distage.model.definition.ModuleDef
import izumi.distage.model.effect._
import izumi.distage.modules.typeclass.BIO3InstancesModule
import izumi.functional.bio.{BIOAsync, BIOAsync3, BIOFork, BIOFork3, BIOLocal, BIOPrimitives, BIOPrimitives3, BIORoot, BIORunner, BIORunner3, BIOTemporal, BIOTemporal3, SyncSafe2, SyncSafe3}
import izumi.reflect.{TagK3, TagKK}

import scala.annotation.unchecked.{uncheckedVariance => v}

/** Any `BIO` effect type support for `distage` resources, effects, roles & tests.
  *
  * For any `F[-_, +_, +_]` with available `make[BIOAsync3[F]]`, `make[BIOTemporal3[F]]` and `make[BIORunner3[F]]` bindings.
  *
  * - Adds [[izumi.distage.model.effect.DIEffect]] instances to support using `F[-_, +_, +_]` in `Injector`, `distage-framework` & `distage-testkit-scalatest`
  * - Adds [[izumi.functional.bio]] typeclass instances for `F[-_, +_, +_]`
  *
  * Depends on `make[BIOAsync3[F]]`, `make[BIOTemporal3[F]]`, `make[BIOLocal[F]]`, `make[BIOFork3[F]]` & `make[BIORunner3[F]]`
  */
class AnyBIO3SupportModule[F[-_, +_, +_]: TagK3](implicit tagBIO: TagKK[F[Any, +?, +?]]) extends ModuleDef {
  // DIEffect & bifunctor bio instances
  include(AnyBIOSupportModule[F[Any, +?, +?]])
  // trifunctor bio instances
  include(BIO3InstancesModule[F])
  addConverted3To2[F[Any, +?, +?]]

  // workaround for
  // - https://github.com/zio/izumi-reflect/issues/82
  // - https://github.com/zio/izumi-reflect/issues/83
  def addConverted3To2[G[+e, +a] >: F[Any, e @v, a @v] <: F[Any, e @v, a @v]: TagKK]: Unit = {
    make[BIOAsync[G]].from {
      implicit F: BIOAsync3[F] => BIORoot.Convert3To2[BIOAsync3, F, Any]
    }
    make[BIOTemporal[G]].from {
      implicit F: BIOTemporal3[F] => BIORoot.Convert3To2[BIOTemporal3, F, Any]
    }
    make[BIOFork[G]].from {
      implicit Fork: BIOFork3[F] => BIORoot.Convert3To2[BIOFork3, F, Any]
    }
    ()
  }
}

object AnyBIO3SupportModule extends App with ModuleDef {
  @inline def apply[F[-_, +_, +_]: TagK3](implicit tagBIO: TagKK[F[Any, +?, +?]]): AnyBIO3SupportModule[F] = new AnyBIO3SupportModule

  /**
    * Make [[AnyBIO3SupportModule]], binding the required dependencies in place to values from implicit scope
    *
    * `make[BIOFork3[F]]` and `make[BIOPrimitives3[F]]` are not required by [[AnyBIO3SupportModule]]
    * but are added for completeness
    */
  def withImplicits[F[-_, +_, +_]: TagK3: BIOAsync3: BIOTemporal3: BIOLocal: BIORunner3: BIOFork3: BIOPrimitives3](implicit tagBIO: TagKK[F[Any, +?, +?]]): ModuleDef =
    new ModuleDef {
      include(AnyBIO3SupportModule[F])

      addImplicit[BIOAsync3[F]]
      addImplicit[BIOTemporal3[F]]
      addImplicit[BIOLocal[F]]
      addImplicit[BIOFork3[F]]
      addImplicit[BIOPrimitives3[F]]
      addImplicit[BIORunner3[F]]

      // no corresponding bifunctor (`F[Any, +?, +?]`) instances need to be added for these types because they already match
      private[this] def aliasingCheck(): Unit = {
        lazy val _ = aliasingCheck()
        implicitly[BIORunner3[F] =:= BIORunner[F[Any, +?, +?]]]
        implicitly[BIOPrimitives3[F] =:= BIOPrimitives[F[Any, +?, +?]]]
        implicitly[SyncSafe3[F] =:= SyncSafe2[F[Any, +?, +?]]]
        implicitly[DIEffectRunner3[F] =:= DIEffectRunner2[F[Any, +?, +?]]]
        implicitly[DIEffect3[F] =:= DIEffect2[F[Any, +?, +?]]]
        implicitly[DIApplicative3[F] =:= DIApplicative2[F[Any, +?, +?]]]
        implicitly[DIEffectAsync3[F] =:= DIEffectAsync2[F[Any, +?, +?]]]
        ()
      }
    }
}
