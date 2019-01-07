package com.github.pshirshov.izumi.functional.mono

import cats.effect.Sync
import com.github.pshirshov.izumi.functional.bio.{BIO, BIOInvariant}

/** Import _exception-safe_ side effects */
trait SyncSafe[+F[_]] {
  /** Suspend an _exception-safe_ side-effect, e.g. random numbers, simple mutation, etc. */
  def syncSafe[A](unexceptionalEff: => A): F[A]
}

object SyncSafe extends LowPrioritySyncSafeInstances {
  def apply[F[_]: SyncSafe]: SyncSafe[F] = implicitly

  implicit def fromBIO[F[+_, +_]: BIO]: SyncSafe[F[Nothing, ?]] =
    new SyncSafe[F[Nothing, ?]] {
      override def syncSafe[A](f: => A): F[Nothing, A] = BIO[F].sync(f)
    }

  implicit def fromBIOInvariant[F[_, _]: BIOInvariant]: SyncSafe[F[Nothing, ?]] =
    new SyncSafe[F[Nothing, ?]] {
      override def syncSafe[A](f: => A): F[Nothing, A] = BIOInvariant[F].sync(f)
    }
}

trait LowPrioritySyncSafeInstances {
  implicit def fromSync[F[_]: Sync]: SyncSafe[F] =
    new SyncSafe[F] {
      override def syncSafe[A](f: => A): F[A] = Sync[F].delay(f)
    }
}
