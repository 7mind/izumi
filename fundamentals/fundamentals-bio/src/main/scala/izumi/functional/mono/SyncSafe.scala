package izumi.functional.mono

import izumi.functional.bio.{F, IO2, IO3, SyncSafe2}
import izumi.fundamentals.orphans.`cats.effect.Sync`

import scala.language.implicitConversions

/** Import _exception-safe_ side effects */
trait SyncSafe[F[_]] {
  /** Suspend an _exception-safe_ side-effect, e.g. random numbers, simple mutation, etc. */
  def syncSafe[A](unexceptionalEff: => A): F[A]

  def widen[G[x] >: F[x]]: SyncSafe[G] = this
}

object SyncSafe extends LowPrioritySyncSafeInstances0 {
  def apply[F[_]: SyncSafe]: SyncSafe[F] = implicitly

  /**
    * This instance uses 'no more orphans' trick to provide an Optional instance
    * only IFF you have cats-effect as a dependency without REQUIRING a cats-effect dependency.
    *
    * Optional instance via https://blog.7mind.io/no-more-orphans.html
    */
  implicit def fromSync[F[_], Sync[_[_]]: `cats.effect.Sync`](implicit F0: Sync[F]): SyncSafe[F] = {
    val F = F0.asInstanceOf[cats.effect.Sync[F]]
    new SyncSafe[F] {
      override def syncSafe[A](f: => A): F[A] = F.delay(f)
    }
  }
}

trait LowPrioritySyncSafeInstances0 extends LowPrioritySyncSafeInstances1 {
  implicit final def fromBIO3[F[-_, +_, +_]: IO3]: SyncSafe[F[Any, Nothing, _]] =
    new SyncSafe[F[Any, Nothing, _]] {
      override def syncSafe[A](f: => A): F[Any, Nothing, A] = F.sync(f)
    }
}

trait LowPrioritySyncSafeInstances1 extends LowPrioritySyncSafeInstances2 {
  implicit final def fromBIO[F[+_, +_]: IO2]: SyncSafe[F[Nothing, _]] =
    new SyncSafe[F[Nothing, _]] {
      override def syncSafe[A](f: => A): F[Nothing, A] = F.sync(f)
    }
}

trait LowPrioritySyncSafeInstances2 {
  /**
    * Emulate covariance. We're forced to employ these because
    * we can't make SyncSafe covariant, because covariant implicits
    * are broken (see scalac bug)
    *
    * Safe because `F` appears only in a covariant position
    *
    * @see https://github.com/scala/bug/issues/11427
    */
  @inline implicit final def limitedCovariance[F[+_, _], E](implicit F: SyncSafe2[F]): SyncSafe[F[E, _]] = F.asInstanceOf[SyncSafe[F[E, _]]]
  @inline implicit final def covarianceConversion[G[_], F[_]](log: SyncSafe[F])(implicit ev: F[?] <:< G[?]): SyncSafe[G] = { val _ = ev; log.asInstanceOf[SyncSafe[G]] }
}
