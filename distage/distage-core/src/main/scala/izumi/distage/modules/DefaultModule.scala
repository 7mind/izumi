package izumi.distage.modules

import izumi.distage.model.definition.{Module, ModuleDef}
import izumi.distage.model.effect.{DIApplicative, DIEffect, DIEffectAsync, DIEffectRunner}
import izumi.distage.modules.support._
import izumi.distage.modules.typeclass.ZIOCatsEffectInstancesModule
import izumi.functional.bio.{BIOAsync, BIOAsync3, BIOFork, BIOFork3, BIOLocal, BIOPrimitives, BIOPrimitives3, BIORunner, BIORunner3, BIOTemporal, BIOTemporal3}
import izumi.fundamentals.orphans._
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.language.unused
import izumi.reflect.{TagK, TagK3, TagKK}

/**
  * Implicitly available effect type support for `distage` resources, effects, roles & tests.
  *
  * Automatically provides default runtime environments & typeclasses instances for effect types.
  * All the defaults are overrideable via [[izumi.distage.model.definition.ModuleDef]]
  *
  * - Adds [[izumi.distage.model.effect.DIEffect]] instances to support using effects in `Injector`, `distage-framework` & `distage-testkit-scalatest`
  * - Adds `cats-effect` typeclass instances for effect types that have `cats-effect` instances
  * - Adds [[izumi.functional.bio]] typeclass instances for bifunctor effect types
  *
  * Currently provides instances for
  *   - `zio`
  *   - `monix-bio`
  *   - `monix`
  *   - `cats-effect` IO
  *   - `Identity`
  *   - Any `F[_]` with `cats-effect` instances
  *   - Any `F[+_, +_]` with [[izumi.functional.bio]] instances
  *   - Any `F[-_, +_, +_]` with [[izumi.functional.bio]] instances
  *   - Any `F[_]` with [[izumi.distage.model.effect.DIEffect]] instances
  */
final case class DefaultModule[F[_]](module: Module) extends AnyVal {
  @inline def of[G[_]]: DefaultModule[G] = new DefaultModule[G](module)
}

object DefaultModule extends LowPriorityDefaultModulesInstances1 {
  @inline def apply[F[_]](implicit modules: DefaultModule[F], d: DummyImplicit): Module = modules.module

  def empty[F[_]]: DefaultModule[F] = DefaultModule(Module.empty)

  /**
    * This instance uses 'no more orphans' trick to provide an Optional instance
    * only IFF you have cats-effect & zio as a dependency without REQUIRING a cats-effect/zio dependency.
    *
    * Optional instance via https://blog.7mind.io/no-more-orphans.html
    *
    * This adds cats typeclass instances to the default effect module if you have cats-effect on classpath,
    * otherwise the default effect module for ZIO will be [[forZIO]], containing BIO & DIEffect, but not cats-effect instances.
    */
  implicit def forZIOPlusCats[K[_], ZIO[_, _, _], R](
    implicit
    @unused ensureCatsEffectOnClasspath: `cats.effect.IO`[K],
    @unused l: `zio.ZIO`[ZIO],
  ): DefaultModule2[ZIO[R, ?, ?]] = {
    DefaultModule(ZIOSupportModule ++ ZIOCatsEffectInstancesModule)
  }

}

sealed trait LowPriorityDefaultModulesInstances1 extends LowPriorityDefaultModulesInstances2 {

  /**
    * This instance uses 'no more orphans' trick to provide an Optional instance
    * only IFF you have zio as a dependency without REQUIRING a zio dependency.
    *
    * Optional instance via https://blog.7mind.io/no-more-orphans.html
    */
  implicit final def forZIO[ZIO[_, _, _]: `zio.ZIO`, R]: DefaultModule2[ZIO[R, ?, ?]] = {
    DefaultModule(ZIOSupportModule)
  }

  /**
    * This instance uses 'no more orphans' trick to provide an Optional instance
    * only IFF you have monix-bio as a dependency without REQUIRING a monix-bio dependency.
    *
    * Optional instance via https://blog.7mind.io/no-more-orphans.html
    *
    * Note: by default this module will implement
    *   - [[monix.execution.Scheduler Scheduler]] using [[monix.execution.Scheduler.global]]
    *   - `Scheduler @Id("io")` using [[monix.execution.Scheduler.io]]
    *   - [[monix.bio.IO.Options]] using [[monix.bio.IO.defaultOptions]]
    *
    * Bindings to the same keys in your own [[izumi.distage.model.definition.ModuleDef]] or plugins will override these defaults.
    */
  implicit final def forMonixBIO[BIO[_, _]: `monix.bio.IO`]: DefaultModule2[BIO] = {
    DefaultModule(MonixBIOSupportModule)
  }

  /**
    * This instance uses 'no more orphans' trick to provide an Optional instance
    * only IFF you have monix as a dependency without REQUIRING a monix dependency.
    *
    * Optional instance via https://blog.7mind.io/no-more-orphans.html
    *
    * Note: by default this module will implement
    *   - [[monix.execution.Scheduler Scheduler]] using [[monix.execution.Scheduler.global]]
    *   - `Scheduler @Id("io")` using [[monix.execution.Scheduler.io]]
    *   - [[monix.eval.Task.Options]] using [[monix.eval.Task.defaultOptions]]
    *
    * Bindings to the same keys in your own [[izumi.distage.model.definition.ModuleDef]] or plugins will override these defaults.
    */
  implicit final def forMonix[Task[_]: `monix.eval.Task`]: DefaultModule[Task] = {
    DefaultModule(MonixSupportModule)
  }

  /**
    * This instance uses 'no more orphans' trick to provide an Optional instance
    * only IFF you have cats-effect as a dependency without REQUIRING a cats-effect dependency.
    *
    * Optional instance via https://blog.7mind.io/no-more-orphans.html
    */
  implicit final def forCatsIO[IO[_]: `cats.effect.IO`]: DefaultModule[IO] = {
    DefaultModule(CatsIOSupportModule)
  }

  /** Empty since [[izumi.distage.modules.support.IdentitySupportModule]] is always available, even for non-Identity effects */
  implicit final def forIdentity: DefaultModule[Identity] = {
    DefaultModule.empty
  }

}

sealed trait LowPriorityDefaultModulesInstances2 extends LowPriorityDefaultModulesInstances3 {
  implicit final def fromBIO[F[+_, +_]: TagKK: BIOAsync: BIOTemporal: BIORunner: BIOFork: BIOPrimitives]: DefaultModule2[F] = {
    DefaultModule(AnyBIOSupportModule.withImplicits[F])
  }
}

sealed trait LowPriorityDefaultModulesInstances3 extends LowPriorityDefaultModulesInstances4 {
  implicit final def fromBIO3[F[-_, +_, +_]: TagK3: BIOAsync3: BIOTemporal3: BIOLocal: BIORunner3: BIOFork3: BIOPrimitives3](
    implicit tagBIO: TagKK[F[Any, +?, +?]]
  ): DefaultModule3[F] = {
    DefaultModule(AnyBIO3SupportModule.withImplicits[F])
  }
}

sealed trait LowPriorityDefaultModulesInstances4 extends LowPriorityDefaultModulesInstances5 {
  /**
    * This instance uses 'no more orphans' trick to provide an Optional instance
    * only IFF you have cats-effect as a dependency without REQUIRING a cats-effect dependency.
    *
    * Optional instance via https://blog.7mind.io/no-more-orphans.html
    */
  implicit final def fromCats[F[_]: TagK, ConcurrentEffect[_[_]], Timer[_[_]], Parallel[_[_]], ContextShift[_[_]]](
    implicit
    @unused l1: `cats.effect.ConcurrentEffect`[ConcurrentEffect],
    @unused l2: `cats.effect.Timer`[Timer],
    @unused l3: `cats.Parallel`[Parallel],
    @unused l4: `cats.effect.ContextShift`[Parallel],
    F0: ConcurrentEffect[F],
    T0: Timer[F],
    P0: Parallel[F],
    C0: ContextShift[F],
  ): DefaultModule[F] = {
    implicit val F: cats.effect.ConcurrentEffect[F] = F0.asInstanceOf[cats.effect.ConcurrentEffect[F]]
    implicit val T: cats.effect.Timer[F] = T0.asInstanceOf[cats.effect.Timer[F]]
    implicit val P: cats.Parallel[F] = P0.asInstanceOf[cats.Parallel[F]]
    implicit val C: cats.effect.ContextShift[F] = C0.asInstanceOf[cats.effect.ContextShift[F]]
    DefaultModule(AnyCatsEffectSupportModule.withImplicits[F])
  }
}

sealed trait LowPriorityDefaultModulesInstances5 {
  implicit final def fromDIEffect[F[_]: TagK: DIEffect: DIEffectAsync: DIEffectRunner]: DefaultModule[F] = {
    DefaultModule(new ModuleDef {
      addImplicit[DIEffect[F]]
      addImplicit[DIEffectAsync[F]]
      addImplicit[DIApplicative[F]]
      addImplicit[DIEffectRunner[F]]
    })
  }
}
