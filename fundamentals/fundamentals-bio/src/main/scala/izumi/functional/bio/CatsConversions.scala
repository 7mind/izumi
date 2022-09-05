package izumi.functional.bio

import cats.data.State
import cats.effect.kernel.*
import cats.effect.kernel.Sync.Type
import cats.effect.{Async, Fiber}
import cats.{Applicative, Eval, Traverse, ~>}
import izumi.functional.bio.CatsConversions.*
import izumi.functional.bio.Exit.CatsExit
import izumi.functional.bio.SpecificityHelper.*
import izumi.functional.bio.data.RestoreInterruption2

import scala.annotation.unused
import scala.concurrent.duration.{FiniteDuration, MILLISECONDS, NANOSECONDS}
import scala.concurrent.{ExecutionContext, Future, TimeoutException}

/**
  * Automatic converters from BIO* hierarchy to equivalent cats & cats-effect classes.
  *
  * {{{
  *   import izumi.functional.bio.IO2
  *   import izumi.functional.bio.catz.*
  *   import cats.effect.kernel.Sync
  *
  *   def divideByZero[F[+_, +_]: IO2]: F[Throwable, Int] = {
  *     Sync[F[Throwable, _]].delay(10 / 0)
  *   }
  * }}}
  */
trait CatsConversions extends CatsConversions1 {
  @inline implicit final def BIOToFunctor[F[+_, +_], E](implicit F0: Functor2[F]): cats.Functor[F[E, _]] & S1 = new BIOCatsFunctor[F, E] {
    override val F: Functor2[F] = F0
  }
}
trait CatsConversions1 extends CatsConversions2 {
  @inline implicit final def BIOToBifunctor[F[+_, +_]](implicit F0: Bifunctor2[F]): cats.Bifunctor[F] & S2 = new BIOCatsBifunctor[F] {
    override val F: Bifunctor2[F] = F0
  }
}
trait CatsConversions2 extends CatsConversions3 {
  @inline implicit final def BIOToApplicative[F[+_, +_], E](implicit F0: Applicative2[F]): cats.Applicative[F[E, _]] & S3 = new BIOCatsApplicative[F, E] {
    override val F: Applicative2[F] = F0
  }
}
trait CatsConversions3 extends CatsConversions4 {
  @inline implicit final def BIOToMonad[F[+_, +_], E](implicit F: Monad2[F]): cats.Monad[F[E, _]] & S4 = new BIOCatsMonad[F, E](F)
}
trait CatsConversions4 extends CatsConversions5 {
  @inline implicit final def BIOToMonadError[F[+_, +_], E](implicit F: Error2[F]): cats.MonadError[F[E, _], E] & S5 = new BIOCatsMonadError[F, E](F)
}
trait CatsConversions5 extends CatsConversions6 {
  @inline implicit final def BIOToClock[F[+_, +_], E](implicit F: Applicative2[F], Clock: Clock2[F]): cats.effect.kernel.Clock[F[E, _]] & S6 =
    new BIOCatsClockImpl[F, E](F, Clock)
}
trait CatsConversions6 extends CatsConversions60 {
  @inline implicit final def BIOToMonadCancel[F[+_, +_]](implicit F: Panic2[F]): cats.effect.kernel.MonadCancel[F[Throwable, _], Throwable] & S7 =
    new BIOCatsMonadCancelImpl[F](F)
}
trait CatsConversions60 extends CatsConversions7 {
  @inline implicit final def BIOToSync[F[+_, +_]](implicit F: IO2[F], blocking: BlockingIO2[F], clock: Clock2[F]): cats.effect.kernel.Sync[F[Throwable, _]] & S8 =
    new BIOCatsSyncImpl[F](F, blocking, clock)
}
trait CatsConversions7 extends CatsConversions8 {
  @inline implicit final def BIOToSpawn[F[+_, +_]](implicit F: IO2[F], FC: Concurrent2[F], Fork: Fork2[F]): cats.effect.kernel.GenSpawn[F[Throwable, _], Throwable] & S9 =
    new BIOCatsSpawnImpl[F](F, FC, Fork)
}
trait CatsConversions8 extends CatsConversions9 {
  @inline implicit final def BIOToConcurrent[F[+_, +_]](
    implicit
    F: IO2[F],
    FC: Concurrent2[F],
    Fork: Fork2[F],
    Primitives: Primitives2[F],
  ): cats.effect.kernel.GenConcurrent[F[Throwable, _], Throwable] & S10 = new BIOCatsConcurrentImpl[F](F, FC, Fork, Primitives)
}
trait CatsConversions9 extends CatsConversions10 {
  @inline implicit final def BIOToParallel[F[+_, +_]](implicit F: Parallel2[F]): cats.Parallel[F[Throwable, _]] = new BIOCatsParallel[F](F)
}
trait CatsConversions10 extends CatsConversions11 {
  @inline implicit final def BIOToTemporal[F[+_, +_]](
    implicit
    F: IO2[F],
    FC: Concurrent2[F],
    FT: Temporal2[F],
    Fork: Fork2[F],
    Primitives: Primitives2[F],
    BlockingIO: BlockingIO2[F],
  ): cats.effect.kernel.GenTemporal[F[Throwable, _], Throwable] & S11 = {
    new BIOCatsTemporalImpl(F, FC, FT, Fork, Primitives, BlockingIO, FT.clock)
  }
}
trait CatsConversions11 {
  @inline implicit final def BIOToAsync[F[+_, +_]](
    implicit @unused ev: Functor2[F],
    F: Async2[F],
    T: Temporal2[F],
    Fork: Fork2[F],
    BlockingIO: BlockingIO2[F],
    Primitives: Primitives2[F],
  ): cats.effect.kernel.Async[F[Throwable, _]] & S12 = new BIOCatsAsync[F](F, F, T, Fork, BlockingIO, T.clock, Primitives)
}

object CatsConversions {

  trait BIOCatsFunctor[F[+_, +_], E] extends cats.Functor[F[E, _]] with S1 with S2 with S3 with S4 with S5 with S6 with S7 with S8 with S9 with S10 with S11 with S12 {
    def F: Functor2[F]

    @inline override final def map[A, B](fa: F[E, A])(f: A => B): F[E, B] = F.map(fa)(f)
    @inline override final def void[A](fa: F[E, A]): F[E, Unit] = F.void(fa)
    @inline override final def widen[A, B >: A](fa: F[E, A]): F[E, B] = fa
    @inline override final def as[A, B](fa: F[E, A], b: B): F[E, B] = F.as(fa)(b)
  }

  trait BIOCatsBifunctor[F[+_, +_]] extends cats.Bifunctor[F] with S2 {
    def F: Bifunctor2[F]

    @inline override final def bimap[A, B, C, D](fab: F[A, B])(f: A => C, g: B => D): F[C, D] = F.bimap(fab)(f, g)
    @inline override final def leftMap[A, B, C](fab: F[A, B])(f: A => C): F[C, B] = F.leftMap(fab)(f)
  }

  trait BIOCatsApplicative[F[+_, +_], E] extends cats.Applicative[F[E, _]] with BIOCatsFunctor[F, E] {
    override def F: Applicative2[F]

    @inline override final def ap[A, B](ff: F[E, A => B])(fa: F[E, A]): F[E, B] = F.map2(ff, fa)(_.apply(_))
    @inline override final def map2[A, B, Z](fa: F[E, A], fb: F[E, B])(f: (A, B) => Z): F[E, Z] = F.map2(fa, fb)(f)
    @inline override final def map2Eval[A, B, Z](fa: F[E, A], fb: Eval[F[E, B]])(f: (A, B) => Z): Eval[F[E, Z]] = Eval.later(F.map2(fa, fb.value)(f))

    @inline override final def pure[A](x: A): F[E, A] = F.pure(x)
    @inline override final def point[A](x: A): F[E, A] = F.pure(x)
    @inline override final def unit: F[E, Unit] = F.unit

    @inline override final def productR[A, B](fa: F[E, A])(fb: F[E, B]): F[E, B] = F.*>(fa, fb)
    @inline override final def productL[A, B](fa: F[E, A])(fb: F[E, B]): F[E, A] = F.<*(fa, fb)

    @inline override final def whenA[A](cond: Boolean)(f: => F[E, A]): F[E, Unit] = F.when(cond)(F.void(f))
    @inline override final def unlessA[A](cond: Boolean)(f: => F[E, A]): F[E, Unit] = F.unless(cond)(F.void(f))
  }

  class BIOCatsMonad[F[+_, +_], E](override val F: Monad2[F]) extends cats.Monad[F[E, _]] with BIOCatsApplicative[F, E] {
    @inline override final def flatMap[A, B](fa: F[E, A])(f: A => F[E, B]): F[E, B] = F.flatMap(fa)(f)
    @inline override final def flatten[A](ffa: F[E, F[E, A]]): F[E, A] = F.flatten(ffa)
    @inline override final def tailRecM[A, B](a: A)(f: A => F[E, Either[A, B]]): F[E, B] = F.tailRecM(a)(f)

    @inline override final def iterateWhile[A](f: F[E, A])(p: A => Boolean): F[E, A] = F.iterateWhile(f)(p)
    @inline override final def iterateUntil[A](f: F[E, A])(p: A => Boolean): F[E, A] = F.iterateUntil(f)(p)
    @inline override final def iterateWhileM[A](init: A)(f: A => F[E, A])(p: A => Boolean): F[E, A] = F.iterateWhileF(init)(f)(p)
    @inline override final def iterateUntilM[A](init: A)(f: A => F[E, A])(p: A => Boolean): F[E, A] = F.iterateUntilF(init)(f)(p)
  }

  class BIOCatsMonadError[F[+_, +_], E](override val F: Error2[F]) extends BIOCatsMonad[F, E](F) with cats.MonadError[F[E, _], E] {
    @inline override final def raiseError[A](e: E): F[E, A] = F.fail(e)
    @inline override final def handleErrorWith[A](fa: F[E, A])(f: E => F[E, A]): F[E, A] = F.catchAll(fa)(f)
    @inline override final def recoverWith[A](fa: F[E, A])(pf: PartialFunction[E, F[E, A]]): F[E, A] = F.catchSome(fa)(pf)

    @inline override final def attempt[A](fa: F[E, A]): F[E, Either[E, A]] = F.attempt(fa)
    @inline override final def fromEither[A](x: Either[E, A]): F[E, A] = F.fromEither(x)
    @inline override final def redeemWith[A, B](fa: F[E, A])(recover: E => F[E, B], bind: A => F[E, B]): F[E, B] = F.redeem(fa)(recover, bind)
  }

  final class BIOCatsMonadCancelImpl[F[+_, +_]](override val F: Panic2[F]) extends BIOCatsMonadCancel[F](F) {
    override lazy val rootCancelScope: cats.effect.kernel.CancelScope = isUninterruptibleNoOp(F)
  }

  abstract class BIOCatsMonadCancel[F[+_, +_]](override val F: Panic2[F])
    extends BIOCatsMonadError[F, Throwable](F)
    with cats.effect.kernel.MonadCancel[F[Throwable, _], Throwable] {

    @inline override final def bracketFull[A, B](
      acquire: Poll[F[Throwable, _]] => F[Throwable, A]
    )(use: A => F[Throwable, B]
    )(release: (A, Outcome[F[Throwable, _], Throwable, B]) => F[Throwable, Unit]
    ): F[Throwable, B] = {
      F.bracketExcept(acquire apply toPoll(_))(
        (a, e: Exit[Throwable, B]) =>
          F.orTerminate {
            // FIXME: we're forced to constrain MonadCancel to Throwable
            //  because there's no branch of Outcome to convert Exit.Terminated to
            //  since we can't convert its Throwable to arbitrary E in Outcome.Errored
            //  and it would be incorrect to convert Exit.Terminated to Outcome.Canceled
            release(a, CatsExit.exitToOutcomeThrowable(e)(F))
          }
      )(use)
    }

    @inline override final def bracketCase[A, B](
      acquire: F[Throwable, A]
    )(use: A => F[Throwable, B]
    )(release: (A, Outcome[F[Throwable, _], Throwable, B]) => F[Throwable, Unit]
    ): F[Throwable, B] = {
      F.bracketCase(acquire)(
        (a, e: Exit[Throwable, B]) =>
          F.orTerminate {
            release(a, CatsExit.exitToOutcomeThrowable(e)(F))
          }
      )(use)
    }

    @inline override final def bracket[A, B](acquire: F[Throwable, A])(use: A => F[Throwable, B])(release: A => F[Throwable, Unit]): F[Throwable, B] = {
      F.bracket(acquire)(e => F.orTerminate(release(e)))(use)
    }

    @inline override final def forceR[A, B](fa: F[Throwable, A])(fb: F[Throwable, B]): F[Throwable, B] = {
      F.redeem(F.sandbox(fa))(
        {
          case exit: Exit.Interruption =>
            F.terminate(new RuntimeException(s"Bad state: Interrupted in forceR, F.sandbox shouldn't be able to catch interruption. exit=$exit", exit.compoundException))
          case _ => fb
        },
        _ => fb,
      )
    }

    @inline override final def uncancelable[A](body: Poll[F[Throwable, _]] => F[Throwable, A]): F[Throwable, A] = {
      F.uninterruptibleExcept(body apply toPoll(_))
    }

    @inline override final def canceled: F[Throwable, Unit] = {
      F.sendInterruptToSelf
    }

    @inline override final def guarantee[A](fa: F[Throwable, A], fin: F[Throwable, Unit]): F[Throwable, A] = {
      F.guarantee(fa, F.orTerminate(fin))
    }
    @inline override final def guaranteeCase[A](fa: F[Throwable, A])(fin: Outcome[F[Throwable, _], Throwable, A] => F[Throwable, Unit]): F[Throwable, A] = {
      F.guaranteeCase(fa, (exit: Exit[Throwable, A]) => F.orTerminate(fin(CatsExit.exitToOutcomeThrowable(exit)(F))))
    }

    @inline override final def onCancel[A](fa: F[Throwable, A], fin: F[Throwable, Unit]): F[Throwable, A] = {
      F.guaranteeOnInterrupt(fa, _ => F.orTerminate(fin))
    }

    @inline protected[this] final def toPoll(restoreInterruption: RestoreInterruption2[F]): Poll[F[Throwable, _]] = {
      new Poll[F[Throwable, _]] {
        override def apply[A](fa: F[Throwable, A]): F[Throwable, A] = restoreInterruption(fa)
      }
    }

  }

  final class BIOCatsClockImpl[F[+_, +_], E](override val F: Applicative2[F], override val Clock: Clock2[F]) extends BIOCatsClock[F, E] with BIOCatsApplicative[F, E] {
    override def applicative: Applicative[F[E, _]] = this
  }

  trait BIOCatsClock[F[+_, +_], E] extends cats.effect.kernel.Clock[F[E, _]] {
    val F: Applicative2[F]
    val Clock: Clock2[F]

    override final def monotonic: F[E, FiniteDuration] = {
      F.map(Clock.monotonicNano)(FiniteDuration(_, NANOSECONDS))
    }

    override final def realTime: F[E, FiniteDuration] = {
      F.map(Clock.epoch)(FiniteDuration(_, MILLISECONDS))
    }
  }

  final class BIOCatsSyncImpl[F[+_, +_]](override val F: IO2[F], override val BlockingIO: BlockingIO2[F], override val Clock: Clock2[F])
    extends BIOCatsSync[F](F, BlockingIO, Clock) {
    override lazy val rootCancelScope: cats.effect.kernel.CancelScope = isUninterruptibleNoOp(F)
  }

  abstract class BIOCatsSync[F[+_, +_]](override val F: IO2[F], val BlockingIO: BlockingIO2[F], override val Clock: Clock2[F])
    extends BIOCatsMonadCancel[F](F)
    with BIOCatsClock[F, Throwable]
    with cats.effect.kernel.Sync[F[Throwable, _]] {

    @inline override final def delay[A](thunk: => A): F[Throwable, A] = {
      F.syncThrowable(thunk)
    }
    @inline override final def blocking[A](thunk: => A): F[Throwable, A] = {
      BlockingIO.syncBlocking(thunk)
    }
    @inline override final def interruptible[A](thunk: => A): F[Throwable, A] = {
      BlockingIO.syncInterruptibleBlocking(thunk)
    }
    @inline override final def interruptibleMany[A](thunk: => A): F[Throwable, A] = {
      BlockingIO.syncInterruptibleBlocking(thunk)
    }
    @inline override final def suspend[A](hint: cats.effect.kernel.Sync.Type)(thunk: => A): F[Throwable, A] = hint match {
      case Type.Delay => delay(thunk)
      case Type.Blocking => blocking(thunk)
      case Type.InterruptibleOnce => interruptible(thunk)
      case Type.InterruptibleMany => interruptibleMany(thunk)
    }

    @inline override final def defer[A](thunk: => F[Throwable, A]): F[Throwable, A] = {
      F.suspend(thunk)
    }

    override def unique: F[Throwable, Unique.Token] = {
      F.sync(new Unique.Token())
    }

  }

  class BIOCatsParallel[F0[+_, +_]](private val F0: Parallel2[F0]) extends cats.Parallel[F0[Throwable, _]] {
    type M[A] = F0[Throwable, A]
    override type F[A] = M[A]

    @inline override final def sequential: F ~> M = cats.arrow.FunctionK.id[M]
    @inline override final def parallel: M ~> F = cats.arrow.FunctionK.id[F]

    override final lazy val applicative: cats.Applicative[F] = new cats.Applicative[F] {
      @inline override final def ap[A, B](ff: F[A => B])(fa: F[A]): F[B] = F0.zipWithPar(ff, fa)(_.apply(_))
      @inline override final def pure[A](x: A): F[A] = F0.InnerF.pure(x): F0[Throwable, A]

      @inline override final def product[A, B](fa: F[A], fb: F[B]): F[(A, B)] = F0.zipPar(fa, fb)
      @inline override final def productL[A, B](fa: F[A])(fb: F[B]): F[A] = F0.zipParLeft(fa, fb)
      @inline override final def productR[A, B](fa: F[A])(fb: F[B]): F[B] = F0.zipParRight(fa, fb)
    }

    override final lazy val monad: cats.Monad[M] = new BIOCatsMonad(F0.InnerF)
  }

  final class BIOCatsSpawnImpl[F[+_, +_]](override val F: IO2[F], override val FC: Concurrent2[F], val Fork: Fork2[F])
    extends BIOCatsMonadCancel[F](F)
    with BIOCatsSpawn[F] {
    override def unique: F[Throwable, Unique.Token] = {
      F.sync(new Unique.Token)
    }
  }

  trait BIOCatsSpawn[F[+_, +_]] extends cats.effect.kernel.GenSpawn[F[Throwable, _], Throwable] {
    val F: Monad2[F]
    val FC: Concurrent2[F]
    val Fork: Fork2[F]

    override def never[A]: F[Throwable, A] = FC.never
    override def cede: F[Throwable, Unit] = FC.yieldNow

    @inline override final def start[A](fa: F[Throwable, A]): F[Throwable, Fiber[F[Throwable, _], Throwable, A]] = {
      F.map(Fork.fork(fa))(_.toCats(F))
    }

    @inline override final def racePair[A, B](
      fa: F[Throwable, A],
      fb: F[Throwable, B],
    ): F[Throwable, Either[
      (Outcome[F[Throwable, _], Throwable, A], Fiber[F[Throwable, _], Throwable, B]),
      (Fiber[F[Throwable, _], Throwable, A], Outcome[F[Throwable, _], Throwable, B]),
    ]] = {
      F.map(FC.racePairUnsafe(fa, fb)) {
        case Left((l, f)) => Left((CatsExit.exitToOutcomeThrowable(l)(F), f.toCats(F)))
        case Right((f, r)) => Right((f.toCats(F), CatsExit.exitToOutcomeThrowable(r)(F)))
      }
    }

    // ZIO.raceFirst does not replicate cats-effect semantic
//    @inline override final def race[A, B](fa: F[Throwable, A], fb: F[Throwable, B]): F[Throwable, Either[A, B]] = {
//      F.race(F.map(fa)(Left(_)), F.map(fb)(Right(_)))
//    }
    override def race[A, B](fa: F[Throwable, A], fb: F[Throwable, B]): F[Throwable, Either[A, B]] = super.race(fa, fb)
    override def background[A](fa: F[Throwable, A]): Resource[F[Throwable, _], F[Throwable, Outcome[F[Throwable, _], Throwable, A]]] = super.background(fa)
    override def raceOutcome[A, B](
      fa: F[Throwable, A],
      fb: F[Throwable, B],
    ): F[Throwable, Either[Outcome[F[Throwable, _], Throwable, A], Outcome[F[Throwable, _], Throwable, B]]] = super.raceOutcome(fa, fb)
    override def both[A, B](fa: F[Throwable, A], fb: F[Throwable, B]): F[Throwable, (A, B)] = super.both(fa, fb)
    override def bothOutcome[A, B](
      fa: F[Throwable, A],
      fb: F[Throwable, B],
    ): F[Throwable, (Outcome[F[Throwable, _], Throwable, A], Outcome[F[Throwable, _], Throwable, B])] = super.bothOutcome(fa, fb)
  }

  final class BIOCatsConcurrentImpl[F[+_, +_]](
    override val F: IO2[F],
    override val FC: Concurrent2[F],
    override val Fork: Fork2[F],
    override val Primitives: Primitives2[F],
  ) extends BIOCatsMonadCancel[F](F)
    with BIOCatsConcurrent[F] {
    override def unique: F[Throwable, Unique.Token] = {
      F.sync(new Unique.Token)
    }
  }

  trait BIOCatsConcurrent[F[+_, +_]] extends cats.effect.kernel.GenConcurrent[F[Throwable, _], Throwable] with BIOCatsSpawn[F] {
    override val FC: Concurrent2[F]
    override val Fork: Fork2[F]
    val Primitives: Primitives2[F]

    override final def ref[A](a: A): F[Throwable, cats.effect.kernel.Ref[F[Throwable, _], A]] = F.map(Primitives.mkRef(a))(
      ref =>
        new cats.effect.kernel.Ref[F[Throwable, _], A] {
          override def get: F[Throwable, A] = ref.get
          override def set(a: A): F[Throwable, Unit] = ref.set(a)
          override def modify[B](f: A => (A, B)): F[Throwable, B] = ref.modify(f(_).swap)
          override def update(f: A => A): F[Throwable, Unit] = ref.update_(f)
          override def tryModify[B](f: A => (A, B)): F[Throwable, Option[B]] = ref.tryModify(f(_).swap)
          override def tryUpdate(f: A => A): F[Throwable, Boolean] = F.map(ref.tryUpdate(f))(_.isDefined)

          override def access: F[Throwable, (A, A => F[Throwable, Boolean])] = {
            F.flatMap(Primitives.mkRef(false)) {
              used =>
                F.map(ref.get) {
                  initialValue =>
                    val setter = (newValue: A) =>
                      F.flatMap(used.modify(old => (old, true)))(
                        alreadyUsed =>
                          if (alreadyUsed) {
                            F.pure(false)
                          } else {
                            ref.modify(
                              oldValue =>
                                // setter only valid if no other modification has occurred concurrently
                                if (oldValue.asInstanceOf[AnyRef] eq initialValue.asInstanceOf[AnyRef]) {
                                  (true, newValue)
                                } else {
                                  (false, oldValue)
                                }
                            )
                          }
                      )
                    (initialValue, setter)
                }
            }
          }

          override def tryModifyState[B](state: State[A, B]): F[Throwable, Option[B]] = {
            val f = state.runF.value
            tryModify(f(_).value)
          }

          override def modifyState[B](state: State[A, B]): F[Throwable, B] = {
            val f = state.runF.value
            modify(f(_).value)
          }
        }
    )

    override final def deferred[A]: F[Throwable, cats.effect.kernel.Deferred[F[Throwable, _], A]] = F.map(Primitives.mkPromise[Nothing, A])(
      promise =>
        new cats.effect.kernel.Deferred[F[Throwable, _], A] {
          override def get: F[Throwable, A] = promise.await
          override def tryGet: F[Throwable, Option[A]] = F.flatMap(promise.poll)(F.traverse(_)(identity))
          override def complete(a: A): F[Throwable, Boolean] = promise.succeed(a)
        }
    )

    override final def memoize[A](fa: F[Throwable, A]): F[Throwable, F[Throwable, A]] = super.memoize(fa)
    override final def parReplicateAN[A](n: Int)(replicas: Int, ma: F[Throwable, A]): F[Throwable, List[A]] = super.parReplicateAN(n)(replicas, ma)
    override final def parSequenceN[T[_], A](n: Int)(tma: T[F[Throwable, A]])(implicit evidence$1: Traverse[T]): F[Throwable, T[A]] = super.parSequenceN(n)(tma)
    override final def parTraverseN[T[_], A, B](n: Int)(ta: T[A])(f: A => F[Throwable, B])(implicit evidence$2: Traverse[T]): F[Throwable, T[B]] =
      super.parTraverseN(n)(ta)(f)
  }

  trait BIOCatsTemporal[F[+_, +_]] extends cats.effect.kernel.GenTemporal[F[Throwable, _], Throwable] with BIOCatsConcurrent[F] {
    val F: Error2[F]
    val FT: Temporal2[F]

    override final def sleep(time: FiniteDuration): F[Throwable, Unit] = FT.sleep(time)

    override final def timeout[A](fa: F[Throwable, A], duration: FiniteDuration)(implicit ev: TimeoutException <:< Throwable): F[Throwable, A] = {
      FT.timeoutFail(duration)(new TimeoutException(duration.toString()), fa)
    }

    override final def timeoutTo[A](fa: F[Throwable, A], duration: FiniteDuration, fallback: F[Throwable, A]): F[Throwable, A] = {
      F.fromOptionF(fallback, FT.timeout(duration)(fa))
    }

    override final def delayBy[A](fa: F[Throwable, A], time: FiniteDuration): F[Throwable, A] = super.delayBy(fa, time)
    override final def andWait[A](fa: F[Throwable, A], time: FiniteDuration): F[Throwable, A] = super.andWait(fa, time)

    override final def timeoutAndForget[A](fa: F[Throwable, A], duration: FiniteDuration)(implicit ev: TimeoutException <:< Throwable): F[Throwable, A] =
      super.timeoutAndForget(fa, duration)(ev)
  }

  class BIOCatsTemporalImpl[F[+_, +_]](
    override val F: IO2[F],
    override val FC: Concurrent2[F],
    override val FT: Temporal2[F],
    override val Fork: Fork2[F],
    override val Primitives: Primitives2[F],
    override val BlockingIO: BlockingIO2[F],
    override val Clock: Clock2[F],
  ) extends BIOCatsSync[F](F, BlockingIO, Clock)
    with BIOCatsTemporal[F] {
    override def unique: F[Throwable, Unique.Token] = super[BIOCatsSync].unique
  }

  class BIOCatsAsync[F[+_, +_]](
    override val F: Async2[F],
    override val FC: Async2[F],
    override val FT: Temporal2[F],
    override val Fork: Fork2[F],
    override val BlockingIO: BlockingIO2[F],
    override val Clock: Clock2[F],
    override val Primitives: Primitives2[F],
  ) extends BIOCatsSync[F](F, BlockingIO, Clock)
    with cats.effect.kernel.Async[F[Throwable, _]]
    with BIOCatsTemporal[F] {

    override def never[A]: F[Throwable, A] = super[BIOCatsTemporal].never
    override def unique: F[Throwable, Unique.Token] = super[BIOCatsSync].unique

    override final def executionContext: F[Throwable, ExecutionContext] = F.currentEC
    override final def evalOn[A](fa: F[Throwable, A], ec: ExecutionContext): F[Throwable, A] = F.onEC(ec)(fa)

    override final def startOn[A](fa: F[Throwable, A], ec: ExecutionContext): F[Throwable, Fiber[F[Throwable, _], Throwable, A]] = {
      F.map(Fork.forkOn(ec)(fa))(_.toCats(F))
    }
    override final def async_[A](k: (Either[Throwable, A] => Unit) => Unit): F[Throwable, A] = F.async(k)
    override final def fromFuture[A](fut: F[Throwable, Future[A]]): F[Throwable, A] = F.flatMap(fut)(F.fromFuture(_))

    override final def cont[K, R](body: Cont[F[Throwable, _], K, R]): F[Throwable, R] = Async.defaultCont(body)(this)

    override final def evalOnK(ec: ExecutionContext): F[Throwable, _] ~> F[Throwable, _] =
      super.evalOnK(ec)
    override final def backgroundOn[A](fa: F[Throwable, A], ec: ExecutionContext): Resource[F[Throwable, _], F[Throwable, Outcome[F[Throwable, _], Throwable, A]]] =
      super.backgroundOn(fa, ec)

    override final def async[A](k: (Either[Throwable, A] => Unit) => F[Throwable, Option[F[Throwable, Unit]]]): F[Throwable, A] =
      F.suspend {
        val p = scala.concurrent.Promise[Either[Throwable, A]]()
        def get: F[Throwable, A] = {
          F.flatMap(F.fromFuture(p.future))(F.fromEither(_))
        }
        F.uninterruptibleExcept(
          restore =>
            F.flatMap(k { e => p.trySuccess(e); () }) {
              case Some(canceler) => F.guaranteeOnInterrupt(restore(get), _ => F.orTerminate(canceler))
              case None => restore(get)
            }
        )
      }

  }

  private[this] def isUninterruptibleNoOp[F[+_, +_]](F: Panic2[F]): cats.effect.kernel.CancelScope = {
    val unit = F.unit
    if (F.uninterruptible(unit).asInstanceOf[AnyRef] eq unit.asInstanceOf[AnyRef]) {
      cats.effect.kernel.CancelScope.Uncancelable
    } else {
      cats.effect.kernel.CancelScope.Cancelable
    }
  }
}
