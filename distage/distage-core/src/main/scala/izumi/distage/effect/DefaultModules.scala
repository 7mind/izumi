package izumi.distage.effect

import izumi.distage.effect.modules._
import izumi.distage.model.definition.{Module, ModuleDef}
import izumi.distage.model.effect.{DIApplicative, DIEffect, DIEffectAsync, DIEffectRunner}
import izumi.fundamentals.orphans._
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.language.unused
import izumi.reflect.{TagK, TagKK}
import monix.execution.Scheduler

final case class DefaultModules[F[_]](modules: Seq[Module]) extends AnyVal

object DefaultModules extends LowPriorityDefaultModulesInstances1 {
  @inline def apply[F[_]: DefaultModules](implicit d: DummyImplicit): DefaultModules[F] = implicitly

  def empty[F[_]]: DefaultModules[F] = DefaultModules(Seq.empty)

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
  ): DefaultModules2[ZIO[R, ?, ?]] = {
    DefaultModules(Seq(ZIODIEffectModule, ZIOCatsTypeclassesModule))
  }

}

sealed trait LowPriorityDefaultModulesInstances1 extends LowPriorityDefaultModulesInstances2 {

  /**
    * This instance uses 'no more orphans' trick to provide an Optional instance
    * only IFF you have zio as a dependency without REQUIRING a zio dependency.
    *
    * Optional instance via https://blog.7mind.io/no-more-orphans.html
    */
  implicit final def forZIO[ZIO[_, _, _]: `zio.ZIO`, R]: DefaultModules2[ZIO[R, ?, ?]] = {
    DefaultModules(Seq(ZIODIEffectModule))
  }

  /**
    * This instance uses 'no more orphans' trick to provide an Optional instance
    * only IFF you have monix-bio as a dependency without REQUIRING a monix-bio dependency.
    *
    * Optional instance via https://blog.7mind.io/no-more-orphans.html
    *
    * @param s is a [[monix.execution.Scheduler Scheduler]] that needs to be available in scope - alternatively, you can override the defaults later in plugins or with `ModuleBase#overridenBy`
    */
  implicit final def forMonixBIO[BIO[_, _], S, Opts](
    implicit
    @unused l1: `monix.bio.IO`[BIO],
    @unused l2: `monix.execution.Scheduler`[S],
    @unused l3: `monix.bio.IO.Options`[Opts],
    s: S = Scheduler.global,
    opts: Opts = monix.bio.IO.defaultOptions,
  ): DefaultModules2[BIO] = {
    DefaultModules(Seq(MonixBIODIEffectModule(s.asInstanceOf[Scheduler], opts.asInstanceOf[monix.bio.IO.Options])))
  }

  /**
    * This instance uses 'no more orphans' trick to provide an Optional instance
    * only IFF you have monix as a dependency without REQUIRING a monix dependency.
    *
    * Optional instance via https://blog.7mind.io/no-more-orphans.html
    *
    * @param s is a [[monix.execution.Scheduler Scheduler]] that needs to be available in scope - alternatively, you can override the defaults later in plugins or with `ModuleBase#overridenBy`
    */
  implicit final def forMonix[Task[_], S, Opts](
    implicit
    @unused l1: `monix.eval.Task`[Task],
    @unused l2: `monix.execution.Scheduler`[S],
    @unused l3: `monix.eval.Task.Options`[Opts],
    s: S = Scheduler.global,
    opts: Opts = monix.eval.Task.defaultOptions,
  ): DefaultModules[monix.eval.Task] = {
    DefaultModules(Seq(MonixDIEffectModule(s.asInstanceOf[Scheduler], opts.asInstanceOf[monix.eval.Task.Options])))
  }

  /**
    * This instance uses 'no more orphans' trick to provide an Optional instance
    * only IFF you have cats-effect as a dependency without REQUIRING a cats-effect dependency.
    *
    * Optional instance via https://blog.7mind.io/no-more-orphans.html
    */
  implicit final def forCatsIO[IO[_]: `cats.effect.IO`]: DefaultModules[IO] = {
    DefaultModules(Seq(CatsDIEffectModule))
  }

  implicit final def forIdentity: DefaultModules[Identity] = {
    DefaultModules(Seq(IdentityDIEffectModule))
  }

}

sealed trait LowPriorityDefaultModulesInstances2 extends LowPriorityDefaultModulesInstances3 {
  implicit final def fromBIO[F[+_, +_]: TagKK]: DefaultModules2[F] = {
    DefaultModules(Seq(???))
  }
}

sealed trait LowPriorityDefaultModulesInstances3 extends LowPriorityDefaultModulesInstances4 {
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
  ): DefaultModules[F] = {
    implicit val F: cats.effect.ConcurrentEffect[F] = F0.asInstanceOf[cats.effect.ConcurrentEffect[F]]
    implicit val T: cats.effect.Timer[F] = T0.asInstanceOf[cats.effect.Timer[F]]
    implicit val P: cats.Parallel[F] = P0.asInstanceOf[cats.Parallel[F]]
    implicit val C: cats.effect.ContextShift[F] = C0.asInstanceOf[cats.effect.ContextShift[F]]
    DefaultModules(Seq(PolymorphicCatsDIEffectModule.withImplicits[F]))
  }
}

sealed trait LowPriorityDefaultModulesInstances4 {
  implicit final def fromDIEffect[F[_]: TagK: DIEffect: DIEffectAsync: DIEffectRunner]: DefaultModules[F] = {
    DefaultModules(Seq(new ModuleDef {
      addImplicit[DIEffect[F]]
      addImplicit[DIEffectAsync[F]]
      addImplicit[DIApplicative[F]]
      addImplicit[DIEffectRunner[F]]
    }))
  }
}
