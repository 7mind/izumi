package izumi.functional.bio

import izumi.functional.bio.DivergenceHelper.{Divergent, Nondivergent}
import izumi.fundamentals.orphans.`cats.effect.kernel.Sync`

import scala.language.implicitConversions

/** Import _exception-safe_ side effects */
trait SyncSafe1[F[_]] extends DivergenceHelper {
  /** Suspend an _exception-safe_ side-effect, e.g. random numbers, simple mutation, etc. */
  def syncSafe[A](unexceptionalEff: => A): F[A]

  @inline final def widen[G[x] >: F[x]]: SyncSafe1[G] = this
}

object SyncSafe1 extends LowPrioritySyncSafeInstances0 {
  def apply[F[_]: SyncSafe1]: SyncSafe1[F] = implicitly

  /**
    * This instance uses 'no more orphans' trick to provide an Optional instance
    * only IFF you have cats-effect as a dependency without REQUIRING a cats-effect dependency.
    *
    * Optional instance via https://blog.7mind.io/no-more-orphans.html
    */
  implicit def fromSync[F[_], Sync[_[_]]: `cats.effect.kernel.Sync`](implicit F0: Sync[F]): SyncSafe1[F] = {
    val F = F0.asInstanceOf[cats.effect.kernel.Sync[F]]
    new SyncSafe1[F] {
      override def syncSafe[A](f: => A): F[A] = F.delay(f)
    }
  }
}

trait LowPrioritySyncSafeInstances0 extends LowPrioritySyncSafeInstances1 {
  implicit final def fromBIO3[F[-_, +_, +_]: IO3]: SyncSafe1[F[Any, Nothing, _]] =
    new SyncSafe1[F[Any, Nothing, _]] {
      override def syncSafe[A](f: => A): F[Any, Nothing, A] = F.sync(f)
    }
}

trait LowPrioritySyncSafeInstances1 extends LowPrioritySyncSafeInstances2 {
  implicit final def fromBIO[F[+_, +_]: IO2]: SyncSafe1[F[Nothing, _]] =
    new SyncSafe1[F[Nothing, _]] {
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
  @inline implicit final def limitedCovariance2[C[f[_]] <: SyncSafe1[f], FR[_, _], R0](
    implicit F: C[FR[Nothing, _]] { type Divergence = Nondivergent }
  ): Divergent.Of[C[FR[R0, _]]] = {
    Divergent(F.asInstanceOf[C[FR[R0, _]]])
  }

  @inline implicit final def limitedCovariance3[C[f[_]] <: SyncSafe1[f], FR[_, _, _], R0, E](
    implicit F: C[FR[Any, Nothing, _]] { type Divergence = Nondivergent }
  ): Divergent.Of[C[FR[R0, E, _]]] = {
    Divergent(F.asInstanceOf[C[FR[R0, E, _]]])
  }

  @inline implicit final def covarianceConversion[F[_], G[_]](syncSafe: SyncSafe1[F])(implicit ev: F[?] <:< G[?]): SyncSafe1[G] = {
    val _ = ev; syncSafe.asInstanceOf[SyncSafe1[G]]
  }
}
