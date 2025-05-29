package izumi.functional.bio

import izumi.functional.bio.data.Isomorphism2
import izumi.functional.lifecycle.Lifecycle

trait Mutex2[F[+_, +_]] {
  def bracket[E, A](f: F[E, A]): F[E, A]
  def bracket_[E, A](f: F[E, A]): F[E, Unit]

  def lifecycle[E]: Lifecycle[F[E, _], Unit]
}

object Mutex2 {
  def createFromBIO[F[+_, +_]: IO2: Primitives2]: F[Nothing, Mutex2[F]] = {
    F.mkSemaphore(1).map {
      semaphore =>
        new Mutex2[F] {
          override def bracket[E, A](f: F[E, A]): F[E, A] = {
            F.bracket(semaphore.acquire)(_ => semaphore.release)(_ => f)
          }
          override def bracket_[E, A](f: F[E, A]): F[E, Unit] = {
            F.bracket(semaphore.acquire)(_ => semaphore.release)(_ => f.void)
          }
          override def lifecycle[E]: Lifecycle[F[E, _], Unit] = {
            Lifecycle.make(semaphore.acquire)(_ => semaphore.release)
          }
        }
    }
  }

  def createFromBIOInterruptible[F[+_, +_]: IO2: Primitives2]: F[Nothing, Mutex2[F]] = {
    F.mkSemaphore(1).map {
      semaphore =>
        new Mutex2[F] {
          override def bracket[E, A](f: F[E, A]): F[E, A] = {
            F.bracketExcept[E, Unit, A](restore => restore(semaphore.acquire))((_, _) => semaphore.release)(_ => f)
          }
          override def bracket_[E, A](f: F[E, A]): F[E, Unit] = {
            F.bracketExcept[E, Unit, Unit](restore => restore(semaphore.acquire))((_, _) => semaphore.release)(_ => f.void)
          }
          override def lifecycle[E]: Lifecycle[F[E, _], Unit] = {
            Lifecycle.makeUninterruptibleExcept[F[E, _], Unit] {
              restore => restore(semaphore.acquire)
            }(_ => semaphore.release)
          }
        }
    }
  }

  implicit final class Mutex2Ops[F[+_, +_]](private val self: Mutex2[F]) extends AnyVal {
    def imapK[G[+_, +_]](fg: F `Isomorphism2` G): Mutex2[G] = new Mutex2[G] {
      override def bracket[E, A](f: G[E, A]): G[E, A] = fg.to(self.bracket(fg.from(f)))
      override def bracket_[E, A](f: G[E, A]): G[E, Unit] = fg.to(self.bracket_(fg.from(f)))
      override def lifecycle[E]: Lifecycle[G[E, _], Unit] = self.lifecycle[E].mapK(fg.to)
    }
  }
}
