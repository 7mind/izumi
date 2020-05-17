package izumi.functional.bio

import cats.Eval
import cats.effect.{CancelToken, Concurrent, ExitCase, Fiber}
import izumi.functional.bio.BIOCatsConversions._
import izumi.functional.bio.SpecificityHelper.{S1, S10, S2, S3, S4, S5, S6, S7, S8, S9}

import scala.util.Either
import cats.~>
import izumi.fundamentals.platform.language.unused

/**
  * Automatic converters from BIO* hierarchy to equivalent cats & cats-effect classes.
  *
  * {{{
  *   import izumi.functional.bio.catz._
  *   import cats.effect.Sync
  *
  *   def divideByZero[F[+_, +_]: BIO]: F[Throwable, Int] = {
  *     Sync[F[Throwable, ?]].delay(10 / 0)
  *   }
  * }}}
  */
trait BIOCatsConversions extends BIOCatsConversions1 {
  @inline implicit final def BIOToFunctor[F[+_, +_], E](implicit F0: BIOFunctor[F]): cats.Functor[F[E, ?]] with S1 = new BIOCatsFunctor[F, E] {
    override val F: BIOFunctor[F] = F0
  }
}
trait BIOCatsConversions1 extends BIOCatsConversions2 {
  @inline implicit final def BIOToBifunctor[F[+_, +_]](implicit F0: BIOBifunctor[F]): cats.Bifunctor[F] with S2 = new BIOCatsBifunctor[F] {
    override val F: BIOBifunctor[F] = F0
  }
}
trait BIOCatsConversions2 extends BIOCatsConversions3 {
  @inline implicit final def BIOToApplicative[F[+_, +_], E](implicit F0: BIOApplicative[F]): cats.Applicative[F[E, ?]] with S3 = new BIOCatsApplicative[F, E] {
    override val F: BIOApplicative[F] = F0
  }
}
trait BIOCatsConversions3 extends BIOCatsConversions4 {
  @inline implicit final def BIOToApplicativeError[F[+_, +_], E](implicit F0: BIOError[F]): cats.ApplicativeError[F[E, ?], E] with S4 =
    new BIOCatsApplicativeError[F, E] {
      override val F: BIOError[F] = F0
    }
}
trait BIOCatsConversions4 extends BIOCatsConversions5 {
  @inline implicit final def BIOToMonad[F[+_, +_], E](implicit F: BIOMonad[F]): cats.Monad[F[E, ?]] with S5 = new BIOCatsMonad[F, E](F)
}
trait BIOCatsConversions5 extends BIOCatsConversions6 {
  @inline implicit final def BIOToMonadError[F[+_, +_], E](implicit F: BIOMonadError[F]): cats.MonadError[F[E, ?], E] with S6 = new BIOCatsMonadError[F, E](F)
}
trait BIOCatsConversions6 extends BIOCatsConversions7 {
  @inline implicit final def BIOToBracket[F[+_, +_]](implicit F: BIOPanic[F]): cats.effect.Bracket[F[Throwable, ?], Throwable] with S7 = new BIOCatsBracket[F](F)
}
trait BIOCatsConversions7 extends BIOCatsConversions8 {
  @inline implicit final def BIOToSync[F[+_, +_]](implicit F: BIO[F]): cats.effect.Sync[F[Throwable, ?]] with S8 = new BIOCatsSync[F](F)
}
trait BIOCatsConversions8 extends BIOCatsConversions9 {
  @inline implicit final def BIOAsyncToAsync[F[+_, +_]](implicit F: BIOAsync[F]): cats.effect.Async[F[Throwable, ?]] with S9 = new BIOCatsAsync[F](F)
}
trait BIOCatsConversions9 extends BIOCatsConversions10 {
  @inline implicit final def BIOParallelToParallel[F[+_, +_]](implicit F: BIOParallel[F]): cats.Parallel[F[Throwable, ?]] = new BIOCatsParallel[F](F)
}
trait BIOCatsConversions10 {
  @inline implicit final def BIOAsyncForkToConcurrent[F[+_, +_]](
    implicit @unused ev: BIOFunctor[F],
    F: BIOAsync[F],
    Fork: BIOFork[F]
  ): cats.effect.Concurrent[F[Throwable, ?]] with S10 = new BIOCatsConcurrent[F](F, Fork)
}

object BIOCatsConversions {

  trait BIOCatsFunctor[F[+_, +_], E] extends cats.Functor[F[E, ?]] with S1 with S2 with S3 with S4 with S5 with S6 with S7 with S8 with S9 with S10 {
    def F: BIOFunctor[F]

    @inline override final def map[A, B](fa: F[E, A])(f: A => B): F[E, B] = F.map(fa)(f)
    @inline override final def void[A](fa: F[E, A]): F[E, Unit] = F.void(fa)
    @inline override final def widen[A, B >: A](fa: F[E, A]): F[E, B] = fa
  }

  trait BIOCatsBifunctor[F[+_, +_]] extends cats.Bifunctor[F] with S1 with S2 with S3 with S4 with S5 with S6 with S7 with S8 with S9 {
    def F: BIOBifunctor[F]

    @inline override final def bimap[A, B, C, D](fab: F[A, B])(f: A => C, g: B => D): F[C, D] = F.bimap(fab)(f, g)
    @inline override final def leftMap[A, B, C](fab: F[A, B])(f: A => C): F[C, B] = F.leftMap(fab)(f)
  }

  trait BIOCatsApplicative[F[+_, +_], E] extends cats.Applicative[F[E, ?]] with BIOCatsFunctor[F, E] {
    override def F: BIOApplicative[F]

    @inline override final def ap[A, B](ff: F[E, A => B])(fa: F[E, A]): F[E, B] = F.map2(ff, fa)(_.apply(_))
    @inline override final def map2[A, B, Z](fa: F[E, A], fb: F[E, B])(f: (A, B) => Z): F[E, Z] = F.map2(fa, fb)(f)
    @inline override final def map2Eval[A, B, Z](fa: F[E, A], fb: Eval[F[E, B]])(f: (A, B) => Z): Eval[F[E, Z]] = Eval.later(F.map2(fa, fb.value)(f))

    @inline override final def pure[A](x: A): F[E, A] = F.pure(x)
    @inline override final def point[A](x: A): F[E, A] = F.pure(x)

    @inline override final def productR[A, B](fa: F[E, A])(fb: F[E, B]): F[E, B] = F.*>(fa, fb)
    @inline override final def productL[A, B](fa: F[E, A])(fb: F[E, B]): F[E, A] = F.<*(fa, fb)
  }

  trait BIOCatsApplicativeError[F[+_, +_], E] extends BIOCatsApplicative[F, E] with cats.ApplicativeError[F[E, ?], E] {
    override def F: BIOError[F]

    @inline override final def raiseError[A](e: E): F[E, A] = F.fail(e)
    @inline override final def handleErrorWith[A](fa: F[E, A])(f: E => F[E, A]): F[E, A] = F.catchAll(fa)(f)
    @inline override final def recoverWith[A](fa: F[E, A])(pf: PartialFunction[E, F[E, A]]): F[E, A] = F.catchSome(fa)(pf)

    @inline override final def attempt[A](fa: F[E, A]): F[E, Either[E, A]] = F.attempt(fa)
    @inline override final def fromEither[A](x: Either[E, A]): F[E, A] = F.fromEither(x)
  }

  class BIOCatsMonad[F[+_, +_], E](override val F: BIOMonad[F]) extends cats.Monad[F[E, ?]] with BIOCatsApplicative[F, E] {
    @inline override final def flatMap[A, B](fa: F[E, A])(f: A => F[E, B]): F[E, B] = F.flatMap(fa)(f)
    @inline override final def flatten[A](ffa: F[E, F[E, A]]): F[E, A] = F.flatten(ffa)
    @inline override final def tailRecM[A, B](a: A)(f: A => F[E, Either[A, B]]): F[E, B] = F.tailRecM(a)(f)
  }

  class BIOCatsMonadError[F[+_, +_], E](override val F: BIOMonadError[F])
    extends BIOCatsMonad[F, E](F)
    with BIOCatsApplicativeError[F, E]
    with cats.MonadError[F[E, ?], E]

  class BIOCatsBracket[F[+_, +_]](override val F: BIOPanic[F]) extends BIOCatsMonadError[F, Throwable](F) with cats.effect.Bracket[F[Throwable, ?], Throwable] {

    @inline override final def bracketCase[A, B](
      acquire: F[Throwable, A]
    )(use: A => F[Throwable, B])(release: (A, ExitCase[Throwable]) => F[Throwable, Unit]): F[Throwable, B] = {
      F.bracketCase(acquire)(
        (a, e: BIOExit[Throwable, B]) =>
          F.orTerminate {
            release(a, e match {
              case BIOExit.Success(_) => ExitCase.Completed
              case value: BIOExit.Failure[Throwable] => ExitCase.Error(value.toThrowable)
            })
          }
      )(use)
    }

    @inline override final def bracket[A, B](acquire: F[Throwable, A])(use: A => F[Throwable, B])(release: A => F[Throwable, Unit]): F[Throwable, B] = {
      F.bracket(acquire)(e => F.orTerminate(release(e)))(use)
    }
  }

  class BIOCatsSync[F[+_, +_]](override val F: BIO[F]) extends BIOCatsBracket[F](F) with cats.effect.Sync[F[Throwable, ?]] {
    @inline override final def suspend[A](thunk: => F[Throwable, A]): F[Throwable, A] = F.flatten(F.syncThrowable(thunk))
    @inline override final def delay[A](thunk: => A): F[Throwable, A] = F.syncThrowable(thunk)
  }

  class BIOCatsParallel[F0[+_, +_]](private val F0: BIOParallel[F0]) extends cats.Parallel[F0[Throwable, ?]] {
    type M[A] = F0[Throwable, A]
    override type F[A] = M[A]

    @inline override final def sequential: F ~> M = cats.arrow.FunctionK.id[M]
    @inline override final def parallel: M ~> F = cats.arrow.FunctionK.id[F]

    override lazy val applicative: cats.Applicative[F] = new cats.Applicative[F] {
      @inline override final def ap[A, B](ff: F[A => B])(fa: F[A]): F[B] = F0.zipWithPar(ff, fa)(_.apply(_))
      @inline override final def pure[A](x: A): F[A] = F0.InnerF.pure(x)

      @inline override final def product[A, B](fa: F[A], fb: F[B]): F[(A, B)] = F0.zipPar(fa, fb)
      @inline override final def productL[A, B](fa: F[A])(fb: F[B]): F[A] = F0.zipParLeft(fa, fb)
      @inline override final def productR[A, B](fa: F[A])(fb: F[B]): F[B] = F0.zipParRight(fa, fb)
    }

    override lazy val monad: cats.Monad[M] = new BIOCatsMonad(F0.InnerF)
  }

  class BIOCatsAsync[F[+_, +_]](override val F: BIOAsync[F]) extends BIOCatsSync[F](F) with cats.effect.Async[F[Throwable, ?]] {
    @inline override final def async[A](k: (Either[Throwable, A] => Unit) => Unit): F[Throwable, A] = F.async(k)
    @inline override final def asyncF[A](k: (Either[Throwable, A] => Unit) => F[Throwable, Unit]): F[Throwable, A] = F.asyncF(k)
    @inline override def liftIO[A](ioa: cats.effect.IO[A]): F[Throwable, A] =
      Concurrent.liftIO(ioa)(new BIOCatsConcurrent[F](F, null)) // Concurrent.liftIO uses only F.cancelable, not fork
    @inline override final def never[A]: F[Throwable, A] = F.never
  }

  class BIOCatsConcurrent[F[+_, +_]](override val F: BIOAsync[F], private val Fork: BIOFork[F]) extends BIOCatsAsync[F](F) with cats.effect.Concurrent[F[Throwable, ?]] {
    @inline override final def start[A](fa: F[Throwable, A]): F[Throwable, Fiber[F[Throwable, *], A]] = {
      F.map(Fork.fork(fa))(_.toCats(F))
    }
    @inline override final def racePair[A, B](
      fa: F[Throwable, A],
      fb: F[Throwable, B]
    ): F[Throwable, Either[(A, Fiber[F[Throwable, *], B]), (Fiber[F[Throwable, *], A], B)]] = {
      F.map(F.racePair(fa, fb)) {
        case Left((a, f)) => Left((a, f.toCats(F)))
        case Right((f, b)) => Right((f.toCats(F), b))
      }
    }
    @inline override final def race[A, B](fa: F[Throwable, A], fb: F[Throwable, B]): F[Throwable, Either[A, B]] = {
      F.race(F.map(fa)(Left(_)), F.map(fb)(Right(_)))
    }
    @inline override final def cancelable[A](k: (Either[Throwable, A] => Unit) => CancelToken[F[Throwable, *]]): F[Throwable, A] = {
      F.asyncCancelable(F orTerminate k(_))
    }
    @inline override final def liftIO[A](ioa: cats.effect.IO[A]): F[Throwable, A] = Concurrent.liftIO(ioa)(this)
//    override def continual[A, B](fa: F[Throwable, A])(f: Either[Throwable, A] => F[Throwable, B]): F[Throwable, B] = super.continual(fa)(f)
  }

}
