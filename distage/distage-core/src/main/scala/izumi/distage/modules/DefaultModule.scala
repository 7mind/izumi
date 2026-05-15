package izumi.distage.modules

import izumi.distage.model.definition.{Module, ModuleDef}
import izumi.distage.modules.support.*
import izumi.distage.modules.typeclass.ZIOCatsEffectInstancesModule
import izumi.functional.bio.retry.Scheduler2
import izumi.functional.bio.{Async2, Bifunctorized, BlockingIO2, Fork2, IO2, Primitives2, PrimitivesLocal2, PrimitivesM2, Temporal2, UnsafeRun2}
import izumi.fundamentals.orphans.*
import izumi.fundamentals.platform.functional.Identity

import scala.annotation.{nowarn, unused}
import izumi.reflect.{Tag, TagK, TagKK}

/**
  * Implicitly available effect type support for `distage` resources, effects, roles & tests.
  *
  * Automatically provides default runtime environments & typeclass instances for effect types.
  * All the defaults are overrideable via [[izumi.distage.model.definition.ModuleDef]]
  *
  *  - Adds [[izumi.functional.bio]] BIO bifunctor instances to support using effects in `Injector`, `distage-framework` & `distage-testkit-scalatest`
  *  - Adds `cats-effect` typeclass instances for effect types that have `cats-effect` instances
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
  *   - Any `F[_]` with `cats-effect` instances (lifted to `Bifunctorized[F, +_, +_]`)
  */
final case class DefaultModule[F[+_, +_]](module: Module) extends AnyVal {
  @inline def to[G[+_, +_]]: DefaultModule[G] = new DefaultModule[G](module)
}

object DefaultModule extends LowPriorityDefaultModulesInstances1 {
  @inline def apply[F[+_, +_]](implicit modules: DefaultModule[F], d: DummyImplicit): Module = modules.module

  def empty[F[+_, +_]]: DefaultModule[F] = DefaultModule(Module.empty)

  /** Bifunctor-shaped partial-application alias for ZIO, used by `forZIO`/`forZIOPlusCats`.
    * Cannot use `ZIO[R, +_, +_]` directly because the no-more-orphans `ZIO[_, _, _]` type
    * variable is declared as invariant. This abstract type is opaque to the variance
    * bookkeeping and erased to `ZIO[R, E, A]` at the JVM level (`Any` placeholder for
    * the kind check). Cast at the value-level is sound: `DefaultModule extends AnyVal` and
    * carries only a `Module` field. The implicit-resolution surface that triggers when a
    * user writes `def m[F[+_, +_]: DefaultModule]` matches on `DefaultModule[ZIO[R, +_, +_]]`
    * via `<:<` against `DefaultModule[ZIOBifunctor[ZIO, R]]` at use sites (or relies on
    * variance widening by `DefaultModule[X[+_, +_]]`'s contravariance — which doesn't exist —
    * so user-facing call sites pass `forZIO[ZIO, R]` explicitly).
    */
  private[modules] type ZIOBifunctor[ZIO[_, _, _], R] = DefaultModule.HKAny
  /** Abstract bifunctor-shaped placeholder kind: `[+E, +A] =>> Any`. Declared as a named
    * type alias because Scala 3 type-lambda syntax `[+E, +A] =>> Any` does not allow
    * variance annotations inline.
    */
  type HKAny[+E, +A] = Any

  /** Empty since [[izumi.distage.modules.support.IdentitySupportModule]] is always available, even for non-Identity effects */
  implicit final def forIdentity: DefaultModule[Bifunctorized.IdentityBifunctorized] = {
    DefaultModule.empty
  }
}

sealed trait LowPriorityDefaultModulesInstances1 extends LowPriorityDefaultModulesInstances2 {
  /**
    * This instance uses 'no more orphans' trick to provide an Optional instance
    * only IFF you have cats-effect & zio as a dependency without REQUIRING a cats-effect/zio dependency.
    *
    * Optional instance via https://blog.7mind.io/no-more-orphans.html
    *
    * This adds cats typeclass instances to the default effect module if you have `cats-effect` and `zio-interop-cats` on classpath,
    * otherwise the default effect module for ZIO will be [[forZIO]], containing BIO instances, but no `cats-effect` instances.
    */
  /** Cast-based return type: declared as `DefaultModule[ZIO[R, ?, ?]]` (the value type that the
    * compiler can construct from the invariant orphan `ZIO[_, _, _]`), with a `private[modules]`
    * type alias [[ZIOBifunctor]] that re-attaches the covariant variance the caller actually wants.
    *
    * Cast is sound: `DefaultModule` is a `case class extends AnyVal` with a single field of type
    * `Module`; variance annotation on F is type-level only and does not affect runtime layout.
    */
  implicit def forZIOPlusCats[K[_[_], _], A[_[_]], ZIO[_, _, _], R](
    implicit
    @unused ensureInteropCatsOnClasspath: `zio.interop.CatsIOResourceSyntax`[K],
    @unused ensureCatsEffectOnClasspath: `cats.effect.kernel.Async`[A],
    @unused isZIO: `zio.ZIO`[ZIO],
    tagR: Tag[R],
  ): DefaultModule[DefaultModule.ZIOBifunctor[ZIO, R]] = {
    new DefaultModule[DefaultModule.ZIOBifunctor[ZIO, R]](ZIOSupportModule[R] ++ ZIOCatsEffectInstancesModule[R])
  }
}

sealed trait LowPriorityDefaultModulesInstances2 extends LowPriorityDefaultModulesInstances3 {
  /**
    * This instance uses 'no more orphans' trick to provide an Optional instance
    * only IFF you have zio as a dependency without REQUIRING a zio dependency.
    *
    * Optional instance via https://blog.7mind.io/no-more-orphans.html
    *
    * @see [[izumi.distage.modules.support.ZIOSupportModule]]
    */
  implicit final def forZIO[ZIO[_, _, _]: `zio.ZIO`, R: Tag]: DefaultModule[zio.ZIO[R, +_, +_]] = {
    new DefaultModule[zio.ZIO[R, +_, +_]](ZIOSupportModule[R])
  }

  /**
    * This instance uses 'no more orphans' trick to provide an Optional instance
    * only IFF you have cats-effect as a dependency without REQUIRING a cats-effect dependency.
    *
    * Optional instance via https://blog.7mind.io/no-more-orphans.html
    *
    * @see [[izumi.distage.modules.support.CatsIOSupportModule]]
    */
  @nowarn("msg=package lang") /* 2.12 false shadowing warning on Java 25+ */
  implicit final def forCatsIO[IO[_]: `cats.effect.IO`]: DefaultModule[Bifunctorized[IO, +_, +_]] = {
    new DefaultModule[Bifunctorized[IO, +_, +_]](CatsIOSupportModule)
  }
}

sealed trait LowPriorityDefaultModulesInstances3 extends LowPriorityDefaultModulesInstances4 {
  /** @see [[izumi.distage.modules.support.AnyBIOSupportModule]] */
  implicit final def fromBIO[
    F[+_, +_]: TagKK: Async2: Temporal2: UnsafeRun2: BlockingIO2: Fork2: Primitives2: PrimitivesM2: PrimitivesLocal2: Scheduler2
  ]: DefaultModule[F] = {
    new DefaultModule[F](AnyBIOSupportModule.usingImplicits[F])
  }
}

sealed trait LowPriorityDefaultModulesInstances4 {
  /**
    * This instance uses 'no more orphans' trick to provide an Optional instance
    * only IFF you have cats-effect as a dependency without REQUIRING a cats-effect dependency.
    *
    * Optional instance via https://blog.7mind.io/no-more-orphans.html
    */
  implicit final def fromCats[F[_], Async[_[_]]: `cats.effect.kernel.Async`, Parallel[_[_]]: `cats.Parallel`, Dispatcher[_[_]]: `cats.effect.std.Dispatcher`](
    implicit
    F0: Async[F],
    P0: Parallel[F],
    D0: Dispatcher[F],
    tagK: TagK[F],
  ): DefaultModule[Bifunctorized[F, +_, +_]] = {
    val F = F0.asInstanceOf[cats.effect.kernel.Async[F]]
    val P = P0.asInstanceOf[cats.Parallel[F]]
    val D = D0.asInstanceOf[cats.effect.std.Dispatcher[F]]
    new DefaultModule[Bifunctorized[F, +_, +_]](AnyCatsEffectSupportModule.withImplicits[F](using tagK, F, P, D))
  }
}
