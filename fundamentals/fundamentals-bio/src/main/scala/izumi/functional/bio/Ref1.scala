package izumi.functional.bio

import izumi.functional.bio.data.~>
import zio.{IO, Ref}

trait Ref1[+F[_], A] {
  def get: F[A]
  def set(a: A): F[Unit]

  def modify[B](f: A => (B, A)): F[B]
  def update(f: A => A): F[A]
  def update_(f: A => A): F[Unit]

  def tryModify[B](f: A => (B, A)): F[Option[B]]
  def tryUpdate(f: A => A): F[Option[A]]
}

object Ref1 {
  def fromZIO[A](ref: Ref[A]): Ref2[IO, A] =
    new Ref2[IO, A] {
      override def get: IO[Nothing, A] = ref.get
      override def set(a: A): IO[Nothing, Unit] = ref.set(a)

      override def modify[B](f: A => (B, A)): IO[Nothing, B] = ref.modify(f)
      override def update(f: A => A): IO[Nothing, A] = ref.updateAndGet(f)
      override def update_(f: A => A): IO[Nothing, Unit] = ref.update(f)

      override def tryUpdate(f: A => A): IO[Nothing, Option[A]] = update(f).map(Some(_)) // zio.Ref does not support try*
      override def tryModify[B](f: A => (B, A)): IO[Nothing, Option[B]] = modify(f).map(Some(_)) // zio.Ref does not support try*
    }

  def fromCats[F[+_, +_]: Panic2, A](ref: cats.effect.Ref[F[Throwable, _], A]): Ref2[F, A] =
    new Ref2[F, A] {
      override def get: F[Nothing, A] = ref.get.orTerminate
      override def set(a: A): F[Nothing, Unit] = ref.set(a).orTerminate

      override def modify[B](f: A => (B, A)): F[Nothing, B] = ref.modify(f(_).swap).orTerminate
      override def update(f: A => A): F[Nothing, A] = ref.updateAndGet(f).orTerminate
      override def update_(f: A => A): F[Nothing, Unit] = ref.update(f).orTerminate

      override def tryModify[B](f: A => (B, A)): F[Nothing, Option[B]] = ref.tryModify(f(_).swap).orTerminate
      override def tryUpdate(f: A => A): F[Nothing, Option[A]] = tryModify(a => { val res = f(a); (res, res) })
    }

  implicit final class Ref1Ops[+F[_], A](private val self: Ref1[F, A]) extends AnyVal {
    def mapK[G[_]](fg: F ~> G): Ref1[G, A] = new Ref1[G, A] {
      override def get: G[A] = fg(self.get)
      override def set(a: A): G[Unit] = fg(self.set(a))
      override def modify[B](f: A => (B, A)): G[B] = fg(self.modify(f))
      override def update(f: A => A): G[A] = fg(self.update(f))
      override def update_(f: A => A): G[Unit] = fg(self.update_(f))
      override def tryModify[B](f: A => (B, A)): G[Option[B]] = fg(self.tryModify(f))
      override def tryUpdate(f: A => A): G[Option[A]] = fg(self.tryUpdate(f))
    }
  }
}
