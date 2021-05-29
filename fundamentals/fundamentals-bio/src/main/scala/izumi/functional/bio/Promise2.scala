package izumi.functional.bio

import cats.effect.concurrent.TryableDeferred
import izumi.functional.bio.data.~>>
import zio.{IO, Promise}

trait Promise2[+F[+_, +_], E, A] {
  def await: F[E, A]
  def poll: F[Nothing, Option[F[E, A]]]

  def succeed(a: A): F[Nothing, Boolean]
  def fail(e: E): F[Nothing, Boolean]
  def terminate(t: Throwable): F[Nothing, Boolean]
}

object Promise2 {
  def fromZIO[E, A](promise: Promise[E, A]): Promise2[IO, E, A] = {
    new Promise2[IO, E, A] {
      override def await: IO[E, A] = promise.await
      override def poll: IO[Nothing, Option[IO[E, A]]] = promise.poll

      override def succeed(a: A): IO[Nothing, Boolean] = promise.succeed(a)
      override def fail(e: E): IO[Nothing, Boolean] = promise.fail(e)
      override def terminate(t: Throwable): IO[Nothing, Boolean] = promise.die(t)
    }
  }

  def fromCats[F[+_, +_]: Panic2, E, A](deferred: TryableDeferred[F[Throwable, `?`], F[E, A]]): Promise2[F, E, A] = {
    new Promise2[F, E, A] {
      override def await: F[E, A] = deferred.get.orTerminate.flatten
      override def poll: F[Nothing, Option[F[E, A]]] = deferred.tryGet.orTerminate

      override def succeed(a: A): F[Nothing, Boolean] = deferred.complete(F.pure(a)).redeemPure(_ => false, _ => true) // .complete throws if already completed
      override def fail(e: E): F[Nothing, Boolean] = deferred.complete(F.fail(e)).redeemPure(_ => false, _ => true)
      override def terminate(t: Throwable): F[Nothing, Boolean] = deferred.complete(F.terminate(t)).redeemPure(_ => false, _ => true)
    }
  }

  implicit final class Promise2Ops[+F[+_, +_], E, A](private val self: Promise2[F, E, A]) extends AnyVal {
    def mapK[G[+_, +_]](fg: F ~>> G)(implicit G: Functor2[G]): Promise2[G, E, A] = new Promise2[G, E, A] {
      override def await: G[E, A] = fg(self.await)
      override def poll: G[Nothing, Option[G[E, A]]] = fg(self.poll).map(_.map(fg(_)))
      override def succeed(a: A): G[Nothing, Boolean] = fg(self.succeed(a))
      override def fail(e: E): G[Nothing, Boolean] = fg(self.fail(e))
      override def terminate(t: Throwable): G[Nothing, Boolean] = fg(self.terminate(t))
    }
  }
}
