package com.github.pshirshov.izumi.functional.mono

import cats.effect.Sync
import com.github.pshirshov.izumi.functional.bio.BIO

/** Import _exception-safe_ side effects */
trait SyncSafe[F[_]] {
  /** Suspend an _exception-safe_ side-effect, e.g. random numbers, simple mutation, etc. */
  def syncSafe[A](unexceptionalEff: => A): F[A]
}

object SyncSafe extends LowPrioritySyncSafeInstances0 {
  def apply[F[_]: SyncSafe]: SyncSafe[F] = implicitly

  implicit def fromBIO[F[_, _]: BIO]: SyncSafe[F[Nothing, ?]] =
    new SyncSafe[F[Nothing, ?]] {
      override def syncSafe[A](f: => A): F[Nothing, A] = BIO[F].sync(f)
    }
}

trait LowPrioritySyncSafeInstances0 extends LowPrioritySyncSafeInstances1 {
  implicit def fromSync[F[_]: Sync]: SyncSafe[F] =
    new SyncSafe[F] {
      override def syncSafe[A](f: => A): F[A] = Sync[F].delay(f)
    }
}

trait LowPrioritySyncSafeInstances1 {
  /**
    * We're forced to employ this because covariant implicits are broken.
    *
    * Safe because `F` appears only in a covariant position
    * */
//  @inline implicit def covariance[G[x] >: F[x], F[_]](implicit ev: SyncSafe[F]): SyncSafe[G] = ev.asInstanceOf[SyncSafe[G]]
}
