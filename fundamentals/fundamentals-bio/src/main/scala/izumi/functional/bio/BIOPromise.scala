package izumi.functional.bio

import zio.{IO, Promise}

trait BIOPromise[+F[+_, +_], E, A] {
  def await: F[E, A]
  def poll: F[Nothing, Option[F[E, A]]]

  def succeed(a: A): F[Nothing, Boolean]
  def fail(e: E): F[Nothing, Boolean]
  def terminate(t: Throwable): F[Nothing, Boolean]
  def interrupt: F[Nothing, Boolean]
}

object BIOPromise {
  def fromZIO[E, A](promise: Promise[E, A]): BIOPromise[IO, E, A] = {
    new BIOPromise[IO, E, A] {
      override def await: IO[E, A]                     = promise.await
      override def poll: IO[Nothing, Option[IO[E, A]]] = promise.poll

      override def succeed(a: A): IO[Nothing, Boolean]           = promise.succeed(a)
      override def fail(e: E): IO[Nothing, Boolean]              = promise.fail(e)
      override def terminate(t: Throwable): IO[Nothing, Boolean] = promise.die(t)
      override def interrupt: IO[Nothing, Boolean]               = promise.interrupt
    }
  }
}
