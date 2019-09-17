package izumi.functional.bio.impl

import izumi.functional.bio.{BIO, BIOAsync, BIOExit}
import zio.{ZIO, ZSchedule}
import zio.clock.Clock
import zio.duration.Duration.fromScala

import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.util.Try

object BIOZio extends BIOZio[Any]

class BIOZio[R] extends BIO[ZIO[R, +?, +?]] with BIOExit.ZIOExit {
  private[this] final type IO[+E, +A] = ZIO[R, E, A]

  @inline override final def pure[A](a: A): IO[Nothing, A] = ZIO.succeed(a)
  @inline override final def sync[A](effect: => A): IO[Nothing, A] = ZIO.effectTotal(effect)
  @inline override final def syncThrowable[A](effect: => A): IO[Throwable, A] = ZIO.effect(effect)

  @inline override final def fail[E](v: => E): IO[E, Nothing] = ZIO.effectTotal(v).flatMap[R, E, Nothing](ZIO.fail)
  @inline override final def terminate(v: => Throwable): IO[Nothing, Nothing] = ZIO.effectTotal(v).flatMap[R, Nothing, Nothing](ZIO.die)

  @inline override final def fromEither[L, R0](v: => Either[L, R0]): IO[L, R0] = ZIO.fromEither(v)
  @inline override final def fromTry[A](effect: => Try[A]): IO[Throwable, A] = ZIO.fromTry(effect)

  @inline override final def void[E, A](r: IO[E, A]): IO[E, Unit] = r.unit
  @inline override final def map[E, A, B](r: IO[E, A])(f: A => B): IO[E, B] = r.map(f)

  @inline override final def leftMap[E, A, E2](r: IO[E, A])(f: E => E2): IO[E2, A] = r.mapError(f)
  @inline override final def leftFlatMap[E, A, E2](r: IO[E, A])(f: E => IO[Nothing, E2]): IO[E2, A] = r.flatMapError(f)
  @inline override final def flip[E, A](r: IO[E, A]): IO[A, E] = r.flip
  @inline override final def bimap[E, A, E2, B](r: IO[E, A])(f: E => E2, g: A => B): IO[E2, B] = r.bimap(f, g)

  @inline override final def flatMap[E, A, E1 >: E, B](r: IO[E, A])(f0: A => IO[E1, B]): IO[E1, B] = r.flatMap(f0)
  @inline override final def flatten[E, A](r: IO[E, IO[E, A]]): IO[E, A] = ZIO.flatten(r)
  @inline override final def *>[E, A, E2 >: E, B](f: IO[E, A], next: => IO[E2, B]): IO[E2, B] = f *> next
  @inline override final def <*[E, A, E2 >: E, B](f: IO[E, A], next: => IO[E2, B]): IO[E2, A] = f <* next
  @inline override final def map2[E, A, E2 >: E, B, C](r1: IO[E, A], r2: => IO[E2, B])(f: (A, B) => C): IO[E2, C] = {
    r1.zipWith(ZIO.effectSuspendTotal(r2))(f)
  }

  @inline override final def redeem[E, A, E2, B](r: IO[E, A])(err: E => IO[E2, B], succ: A => IO[E2, B]): IO[E2, B] = r.foldM(err, succ)
  @inline override final def catchAll[E, A, E2, A2 >: A](r: IO[E, A])(f: E => IO[E2, A2]): IO[E2, A2] = r.catchAll(f)
  @inline override final def catchSome[E, A, E2 >: E, A2 >: A](r: ZIO[R, E, A])(f: PartialFunction[E, ZIO[R, E2, A2]]): ZIO[R, E2, A2] = r.catchSome(f)

  @inline override final def guarantee[E, A](f: IO[E, A])(cleanup: IO[Nothing, Unit]): IO[E, A] = {
    ZIO.accessM(r => f.ensuring(cleanup.provide(r)))
  }
  @inline override final def attempt[E, A](r: IO[E, A]): IO[Nothing, Either[E, A]] = r.either
  @inline override final def redeemPure[E, A, B](r: IO[E, A])(err: E => B, succ: A => B): IO[Nothing, B] = r.fold(err, succ)

  @inline override final def bracket[E, A, B](acquire: IO[E, A])(release: A => IO[Nothing, Unit])(use: A => IO[E, B]): IO[E, B] = {
    ZIO.bracket(acquire)(v => release(v))(use)
  }

  @inline override final def bracketCase[E, A, B](acquire: IO[E, A])(release: (A, BIOExit[E, B]) => IO[Nothing, Unit])(use: A => IO[E, B]): IO[E, B] = {
    ZIO.bracketExit[R, E, A, B](acquire, { case (a, exit) => release(a, toBIOExit(exit)) }, use)
  }

  @inline override final def traverse[E, A, B](l: Iterable[A])(f: A => IO[E, B]): IO[E, List[B]] = ZIO.foreach(l)(f)

  @inline override final def sandbox[E, A](r: IO[E, A]): IO[BIOExit.Failure[E], A] = r.sandbox.mapError(toBIOExit[E])
}

class BIOAsyncZio[R](clockService: Clock) extends BIOZio[R] with BIOAsync[ZIO[R, +?, +?]] {
  private[this] final type IO[+E, +A] = ZIO[R, E, A]

  @inline override final def `yield`: IO[Nothing, Unit] = ZIO.yieldNow

  @inline override final def sleep(duration: Duration): IO[Nothing, Unit] = {
    ZIO.sleep(fromScala(duration)).provide(clockService)
  }

  @inline override final def retryOrElse[A, E, A2 >: A, E2](r: IO[E, A])(duration: FiniteDuration, orElse: => IO[E2, A2]): IO[E2, A2] =
    ZIO.accessM { env =>
      r.provide(env).retryOrElse(ZSchedule.duration(fromScala(duration)), {
        (_: Any, _: Any) =>
          orElse.provide(env)
      }).provide(clockService)
    }

  @inline override final def timeout[E, A](r: IO[E, A])(duration: Duration): IO[E, Option[A]] = {
    ZIO.accessM[R](r.provide(_).timeout(fromScala(duration)).provide(clockService))
  }

  @inline override final def race[E, A](r1: IO[E, A])(r2: IO[E, A]): IO[E, A] = {
    r1.race(r2)
  }

  @inline override final def async[E, A](register: (Either[E, A] => Unit) => Unit): IO[E, A] = {
    ZIO.effectAsync[R, E, A] {
      cb =>
        register {
          case Right(v) =>
            cb(ZIO.succeed(v))
          case Left(t) =>
            cb(ZIO.fail(t))
        }
    }
  }

  @inline override final def asyncCancelable[E, A](register: (Either[E, A] => Unit) => Canceler): IO[E, A] = {
    ZIO.accessM {
      r =>
        ZIO.effectAsyncInterrupt[R, E, A] {
          cb =>
            val canceler = register {
              case Right(v) =>
                cb(ZIO.succeed(v))
              case Left(t) =>
                cb(ZIO.fail(t))
            }
            Left(canceler.provide(r))
        }
    }
  }

  @inline override final def uninterruptible[E, A](r: IO[E, A]): IO[E, A] = {
    r.uninterruptible
  }

  @inline override final def parTraverseN[E, A, B](maxConcurrent: Int)(l: Iterable[A])(f: A => IO[E, B]): IO[E, List[B]] = {
    ZIO.foreachParN(maxConcurrent)(l)(f)
  }
}
