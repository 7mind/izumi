package izumi.functional.lifecycle

import izumi.functional.bio.{Applicative1, Bifunctorized, IO1, IO2, Primitives1}

/** Parallel BIO surface for [[Lifecycle]] factory methods.
  *
  * Mirrors a strict subset of `Lifecycle.{make, makePair, liftF, pure, suspend, fail, unit}` but
  * accepts BIO-shaped inputs constrained by `IO2[Bifunctorized.NoOp[F, +_, +_]]` (the no-op
  * bifunctor wrapper provided by [[izumi.functional.bio.BifunctorizedNoOpInstances]]) instead of
  * the `IO1` / `Primitives1` / `Applicative1` family. The intent is to give callers
  * holding a real bifunctor `F[+_, +_]: IO2` a path to construct `Lifecycle[F[Throwable, _], A]`
  * values without crossing the `IO1` ABI.
  *
  * The produced `Lifecycle[F[Throwable, _], A]` is over the user-visible monofunctor
  * `F[Throwable, _]`, NOT over the `Bifunctorized` wrapper type — at runtime
  * `Bifunctorized.NoOp[F, Throwable, A]` IS `F[Throwable, A]` (the abstract type is erased to
  * `Object` and carries the underlying bifunctor instance through `asInstanceOf`), so the
  * existing `Lifecycle` instance is the right one and no extra allocation occurs.
  *
  * Bridging strategy: the existing `IO1.fromBIO` derivation
  * ([[izumi.functional.bio.LowPriorityIO1Instances#fromBIO]]) already produces a
  * `IO1[Bifunctorized.NoOp[F, Throwable, _]]` from `IO2[Bifunctorized.NoOp[F, +_, +_]]`.
  * Because `Bifunctorized.NoOp[F, Throwable, A]` is erased to `F[Throwable, A]` at runtime,
  * that dictionary IS a `IO1[F[Throwable, _]]` modulo type. The reinterpret cast in
  * [[asIO1]] is therefore sound and zero-cost.
  *
  * This is the M3-PR1 entry point that unblocks M4 (Injector's BIO-constrained `apply`). The
  * in-place rewrite of `Lifecycle.scala`'s 44 `Quasi*` sites is deferred to M5, where the entire
  * `Quasi*` family is deleted.
  */
object LifecycleBifunctorized {

  /** Reinterpret a `IO1[Bifunctorized.NoOp[F, Throwable, _]]` (obtained via the existing
    * `IO1.fromBIO` derivation) as a `IO1[F[Throwable, _]]`. Sound because
    * `Bifunctorized.NoOp[F, Throwable, A]` is erased to `F[Throwable, A]` — every method on the
    * dictionary takes/returns values that ARE `F[Throwable, ?]` at the JVM level.
    */
  @inline private def asIO1[F[+_, +_]](
    implicit F: IO2[Bifunctorized.NoOp[F, +_, +_]]
  ): IO1[F[Throwable, _]] = {
    val onWrapper: IO1[Bifunctorized.NoOp[F, Throwable, _]] = implicitly[IO1[Bifunctorized.NoOp[F, Throwable, _]]]
    onWrapper.asInstanceOf[IO1[F[Throwable, _]]]
  }

  /** @see [[Lifecycle.make]] */
  def make[F[+_, +_], A](
    acquire: => Bifunctorized.NoOp[F, Throwable, A]
  )(release: A => Bifunctorized.NoOp[F, Throwable, Unit]
  )(implicit @scala.annotation.unused F: IO2[Bifunctorized.NoOp[F, +_, +_]]
  ): Lifecycle[F[Throwable, _], A] = {
    Lifecycle.make[F[Throwable, _], A](acquire.unwrap)(a => release(a).unwrap)
  }

  /** @see [[Lifecycle.makePair]] */
  def makePair[F[+_, +_], A](
    allocate: Bifunctorized.NoOp[F, Throwable, (A, Bifunctorized.NoOp[F, Throwable, Unit])]
  )(implicit F: IO2[Bifunctorized.NoOp[F, +_, +_]]
  ): Lifecycle[F[Throwable, _], A] = {
    implicit val Q: IO1[F[Throwable, _]] = asIO1[F]
    val fInner: F[Throwable, (A, F[Throwable, Unit])] =
      Q.map(allocate.unwrap) { case (a, releaseB) => (a, releaseB.unwrap) }
    Lifecycle.makePair[F[Throwable, _], A](fInner)
  }

  /** @see [[Lifecycle.liftF]] */
  def liftF[F[+_, +_], A](
    effect: => Bifunctorized.NoOp[F, Throwable, A]
  )(implicit F: IO2[Bifunctorized.NoOp[F, +_, +_]]
  ): Lifecycle[F[Throwable, _], A] = {
    implicit val Q: Applicative1[F[Throwable, _]] = asIO1[F]
    Lifecycle.liftF[F[Throwable, _], A](effect.unwrap)
  }

  /** @see [[Lifecycle.pure]] */
  def pure[F[+_, +_], A](a: A)(implicit F: IO2[Bifunctorized.NoOp[F, +_, +_]]): Lifecycle[F[Throwable, _], A] = {
    implicit val Q: Applicative1[F[Throwable, _]] = asIO1[F]
    Lifecycle.pure[F[Throwable, _]](a)
  }

  /** @see [[Lifecycle.suspend]] */
  def suspend[F[+_, +_], A](
    effect: => Bifunctorized.NoOp[F, Throwable, Lifecycle[F[Throwable, _], A]]
  )(implicit F: IO2[Bifunctorized.NoOp[F, +_, +_]]
  ): Lifecycle[F[Throwable, _], A] = {
    implicit val Q: Primitives1[F[Throwable, _]] = asIO1[F]
    Lifecycle.suspend[F[Throwable, _], A](effect.unwrap)
  }

  /** @see [[Lifecycle.fail]] */
  def fail[F[+_, +_], A](
    error: => Throwable
  )(implicit F: IO2[Bifunctorized.NoOp[F, +_, +_]]
  ): Lifecycle[F[Throwable, _], A] = {
    implicit val Q: IO1[F[Throwable, _]] = asIO1[F]
    Lifecycle.fail[F[Throwable, _], A](error)
  }

  /** @see [[Lifecycle.unit]] */
  def unit[F[+_, +_]](implicit F: IO2[Bifunctorized.NoOp[F, +_, +_]]): Lifecycle[F[Throwable, _], Unit] = {
    implicit val Q: Applicative1[F[Throwable, _]] = asIO1[F]
    Lifecycle.unit[F[Throwable, _]]
  }

}
