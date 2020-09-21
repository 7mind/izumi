package izumi.functional.bio

import zio.{IO, Ref}

trait BIORef[+F[_, _], A] {
  def get: F[Nothing, A]
  def set(a: A): F[Nothing, Unit]

  def modify[B](f: A => (B, A)): F[Nothing, B]
  def update(f: A => A): F[Nothing, A]
  def update_(f: A => A): F[Nothing, Unit]
}

object BIORef {
  def fromZIO[A](ref: Ref[A]): BIORef[IO, A] =
    new BIORef[IO, A] {
      override def get: IO[Nothing, A] = ref.get
      override def set(a: A): IO[Nothing, Unit] = ref.set(a)

      override def modify[B](f: A => (B, A)): IO[Nothing, B] = ref.modify(f)
      override def update(f: A => A): IO[Nothing, A] = ref.updateAndGet(f)
      override def update_(f: A => A): IO[Nothing, Unit] = ref.update(f)
    }

  def fromCats[F[+_, +_]: BIOPanic, A](ref: cats.effect.concurrent.Ref[F[Throwable, ?], A]): BIORef[F, A] =
    new BIORef[F, A] {
      override def get: F[Nothing, A] = ref.get.orTerminate
      override def set(a: A): F[Nothing, Unit] = ref.set(a).orTerminate
      override def modify[B](f: A => (B, A)): F[Nothing, B] = ref.modify(f(_).swap).orTerminate
      override def update(f: A => A): F[Nothing, A] = ref.updateAndGet(f).orTerminate
      override def update_(f: A => A): F[Nothing, Unit] = ref.update(f).orTerminate
    }
}
