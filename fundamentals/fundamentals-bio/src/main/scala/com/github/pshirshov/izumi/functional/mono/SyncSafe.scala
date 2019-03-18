package com.github.pshirshov.izumi.functional.mono

import cats.effect.Sync
import com.github.pshirshov.izumi.functional.bio.{BIO, BIOInvariant, SyncSafe2}

import scala.language.implicitConversions

/** Import _exception-safe_ side effects */
trait SyncSafe[F[_]] {
  /** Suspend an _exception-safe_ side-effect, e.g. random numbers, simple mutation, etc. */
  def syncSafe[A](unexceptionalEff: => A): F[A]
}

object SyncSafe extends LowPrioritySyncSafeInstances0 {
  def apply[F[_]: SyncSafe]: SyncSafe[F] = implicitly

  implicit def fromSync[F[_]: Sync]: SyncSafe[F] =
    new SyncSafe[F] {
      override def syncSafe[A](f: => A): F[A] = Sync[F].delay(f)
    }
}

trait LowPrioritySyncSafeInstances0 extends LowPrioritySyncSafeInstances1 {
  implicit def fromBIO[F[+_, +_]: BIO]: SyncSafe[F[Nothing, ?]] =
    new SyncSafe[F[Nothing, ?]] {
      override def syncSafe[A](f: => A): F[Nothing, A] = BIO[F].sync(f)
    }
}

trait LowPrioritySyncSafeInstances1 {
  implicit def fromBIOInvariant[F[_, _]: BIOInvariant]: SyncSafe[F[Nothing, ?]] =
    new SyncSafe[F[Nothing, ?]] {
      override def syncSafe[A](f: => A): F[Nothing, A] = BIOInvariant[F].sync(f)
    }

  /**
    * Emulate covariance. We're forced to employ these because
    * we can't make SyncSafe covariant, because covariant implicits
    * are broken (see scalac bug)
    *
    * Safe because `F` appears only in a covariant position
    *
    * @see https://github.com/scala/bug/issues/11427
    */
  implicit def limitedCovariance[F[+_, _], E](implicit F: SyncSafe2[F]): SyncSafe[F[E, ?]] = F.asInstanceOf[SyncSafe[F[E, ?]]]
  implicit def covarianceConversion[G[_], F[_]](log: SyncSafe[F])(implicit ev: F[_] <:< G[_]): SyncSafe[G] = { val _ = ev; log.asInstanceOf[SyncSafe[G]] }
}
