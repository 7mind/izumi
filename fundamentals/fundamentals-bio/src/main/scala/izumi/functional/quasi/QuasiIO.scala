package izumi.functional.quasi

import cats.effect.kernel.Outcome
import izumi.functional.bio.data.{Morphism1, RestoreInterruption1}
import izumi.functional.bio.{Applicative2, Exit, Functor2, IO2, TypedError}
import izumi.functional.quasi.QuasiIO.QuasiIOIdentity
import izumi.fundamentals.orphans.{`cats.Applicative`, `cats.Functor`, `cats.effect.kernel.Sync`}
import izumi.fundamentals.platform.functional.Identity

import java.util.concurrent.atomic.AtomicReference
import scala.annotation.tailrec
import scala.language.implicitConversions
import scala.util.{Failure, Success, Try}

/**
  * Evidence that `F` is _almost_ like an `IO` monad, but not quite –
  * because we also allow an impure [[izumi.fundamentals.platform.functional.Identity]] instance,
  * for which `maybeSuspend` does not actually suspend!
  *
  * If you use this interface and forget to add manual suspensions with by-name parameters everywhere,
  * you're going to get weird behavior for Identity instance.
  *
  * This interface serves internal needs of `distage` for interoperability with all existing
  * Scala effect types and also with `Identity`, you should NOT refer to it in your code if possible.
  *
  * This type is public because you may want to define your own instances, if a suitable instance of [[izumi.distage.modules.DefaultModule]]
  * is missing for your custom effect type.
  * For application logic, prefer writing against typeclasses in [[izumi.functional.bio]] or [[cats]] instead.
  *
  * @see [[izumi.distage.modules.DefaultModule]] - `DefaultModule` makes instances of `QuasiIO` for cats-effect, ZIO,
  *      monix, monix-bio, `Identity` and others available for summoning in your wiring automatically
  *
  * TODO: we want to get rid of this by providing Identity implementations for relevant BIO typeclasses
  * See https://github.com/7mind/izumi/issues/787
  */
trait QuasiIO[F[_]] extends QuasiPrimitives[F] {
  def flatMap[A, B](fa: F[A])(f: A => F[B]): F[B]

  def guaranteeOnFailure[A](fa: => F[A])(cleanupOnFailure: Throwable => F[Unit]): F[A] = {
    bracketCase(acquire = unit)(release = (_, maybeExit) => maybeExit.fold(unit)(cleanupOnFailure))(use = _ => fa)
  }
  def guaranteeOnInterrupt[A](fa: => F[A])(cleanupOnInterrupt: Exit.Trace[Nothing] => F[Unit]): F[A]
  def bracketCase[A, B](acquire: => F[A])(release: (A, Option[Throwable]) => F[Unit])(use: A => F[B]): F[B]
  final def bracketAuto[A <: AutoCloseable, B](acquire: => F[A])(use: A => F[B]): F[B] = bracket(acquire)(a => maybeSuspend(a.close()))(use)

  /** A weaker version of `delay`. Does not guarantee _actual_
    * suspension of side-effects, because QuasiIO[Identity] is allowed
    */
  def maybeSuspend[A](eff: => A): F[A]

  def maybeSuspendEither[A](eff: => Either[Throwable, A]): F[A]

  /**
    * A stronger version of `handleErrorWith` (cats-effect) or `catchAll` (ZIO),
    * the difference is that this will _also_ intercept Throwable defects in `ZIO`,
    * not only typed errors.
    *
    * @note This function is meant for _RECOVERING_ errors, for _REPORTING_ errors use [[definitelyRecoverWithTrace]]
    *       and include the full trace of the error in the report.
    */
  def definitelyRecoverUnsafeIgnoreTrace[A](action: => F[A])(recover: Throwable => F[A]): F[A]

  /**
    * Like [[definitelyRecoverUnsafeIgnoreTrace]], but the second parameter to the callback
    * contains the effect's debugging information, possibly convertable to a Throwable via [[Exit.Trace#toThrowable]],
    * or that could be used to mutably enhance the left-hand-side Throwable value via [[Exit.Trace#unsafeAttachTrace]]
    *
    * @note [[Exit.Trace#unsafeAttachTrace]] may perform side-effects to the original Throwable argument on the left,
    * the left throwable should be DISCARDED after calling the callback.
    * (e.g. in case of `ZIO`, the callback will mutate the throwable and attach a ZIO Trace to it.)
    */
  def definitelyRecoverWithTrace[A](action: => F[A])(recoverWithTrace: (Throwable, Exit.Trace[Throwable]) => F[A]): F[A]

  def redeem[A, B](action: => F[A])(failure: Throwable => F[B], success: A => F[B]): F[B]

  def fail[A](t: => Throwable): F[A]

  override def suspendF[A](effAction: => F[A]): F[A] = {
    flatMap(maybeSuspend(effAction))(identity)
  }

  override def mkRef[A](a: A): F[QuasiRef[F, A]] = {
    QuasiRef.fromMaybeSuspend(a)(Morphism1(f => maybeSuspend(f())))
  }
}

object QuasiIO extends LowPriorityQuasiIOInstances {
  @inline def apply[F[_]: QuasiIO]: QuasiIO[F] = implicitly

  object syntax {
    implicit def suspendedSyntax[F[_], A](fa: => F[A]): QuasiIOSuspendedSyntax[F, A] = new QuasiIOSuspendedSyntax(() => fa)

    implicit final class QuasiIOSyntax[F[_], A](private val fa: F[A]) extends AnyVal {
      @inline def map[B](f: A => B)(implicit F: QuasiFunctor[F]): F[B] = F.map(fa)(f)
      @inline def flatMap[B](f: A => F[B])(implicit F: QuasiPrimitives[F]): F[B] = F.flatMap(fa)(f)
    }

    final class QuasiIOSuspendedSyntax[F[_], A](private val fa: () => F[A]) extends AnyVal {
      @inline def guarantee(`finally`: => F[Unit])(implicit F: QuasiPrimitives[F]): F[A] = {
        F.guarantee(fa())(`finally`)
      }
      @inline def guaranteeOnFailure(cleanupOnFailure: Throwable => F[Unit])(implicit F: QuasiIO[F]): F[A] = {
        F.guaranteeOnFailure(fa())(cleanupOnFailure)
      }
      @inline def guaranteeOnInterrupt(cleanupOnInterrupt: Exit.Trace[Nothing] => F[Unit])(implicit F: QuasiIO[F]): F[A] = {
        F.guaranteeOnInterrupt(fa())(cleanupOnInterrupt)
      }
    }
  }

  @inline implicit def quasiIOIdentity: QuasiIO[Identity] = QuasiIOIdentity

  private[quasi] object QuasiIOIdentity extends QuasiIO[Identity] {
    override def pure[A](a: A): Identity[A] = a
    override def map[A, B](fa: Identity[A])(f: A => B): Identity[B] = f(fa)
    override def map2[A, B, C](fa: Identity[A], fb: => Identity[B])(f: (A, B) => C): Identity[C] = f(fa, fb)
    override def flatMap[A, B](a: A)(f: A => Identity[B]): Identity[B] = f(a)
    @tailrec override def tailRecM[A, B](a: A)(f: A => Identity[Either[A, B]]): Identity[B] = {
      f(a) match {
        case Left(next) => tailRecM(next)(f)
        case Right(res) => res
      }
    }

    override def maybeSuspend[A](eff: => A): Identity[A] = eff
    override def maybeSuspendEither[A](eff: => Either[Throwable, A]): Identity[A] = eff match {
      case Left(err) => throw err
      case Right(v) => v
    }
    override def suspendF[A](effAction: => A): Identity[A] = effAction
    override def definitelyRecoverUnsafeIgnoreTrace[A](fa: => Identity[A])(recover: Throwable => Identity[A]): Identity[A] = {
      try { fa }
      catch { case t: Throwable => recover(t) }
    }
    override def definitelyRecoverWithTrace[A](action: => Identity[A])(recoverCause: (Throwable, Exit.Trace[Throwable]) => Identity[A]): Identity[A] = {
      definitelyRecoverUnsafeIgnoreTrace(action)(e => recoverCause(e, Exit.Trace.ThrowableTrace(e)))
    }
    override def redeem[A, B](action: => Identity[A])(failure: Throwable => Identity[B], success: A => Identity[B]): Identity[B] = {
      TryNonFatal(action) match {
        case Failure(exception) =>
          failure(exception)
        case Success(value) =>
          success(value)
      }
    }

    override def tapBothUntyped[A](eff: => Identity[A])(err: Any => Identity[Unit], succ: A => Identity[Unit]): Identity[A] = {
      TryNonFatal(eff) match {
        case Failure(exception) => err(exception); throw exception
        case Success(value) => succ(value); value
      }
    }

    override def bracket[A, B](acquire: => Identity[A])(release: A => Identity[Unit])(use: A => Identity[B]): Identity[B] = {
      val a = acquire
      try use(a)
      finally release(a)
    }
    override def bracketCase[A, B](acquire: => Identity[A])(release: (A, Option[Throwable]) => Identity[Unit])(use: A => Identity[B]): Identity[B] = {
      val a = acquire
      TryWithFatal(use(a)) match {
        case Failure(exception) =>
          release(a, Some(exception))
          throw exception
        case Success(value) =>
          release(a, None)
          value
      }
    }
    override def uninterruptibleExcept[A](f: RestoreInterruption1[Identity] => Identity[A]): Identity[A] = {
      f(Morphism1.identity[Identity])
    }
    override def guarantee[A](fa: => Identity[A])(`finally`: => Identity[Unit]): Identity[A] = {
      try { fa }
      finally `finally`
    }
    override def guaranteeOnFailure[A](fa: => Identity[A])(cleanupOnFailure: Throwable => Identity[Unit]): Identity[A] = {
      try { fa }
      catch { case t: Throwable => cleanupOnFailure(t); throw t }
    }
    override def guaranteeOnInterrupt[A](fa: => Identity[A])(cleanupOnInterrupt: Exit.Trace[Nothing] => Identity[Unit]): Identity[A] = {
      try { fa }
      catch {
        case t: InterruptedException => cleanupOnInterrupt(Exit.Trace.forThrowable(t)); throw t
      }
    }
    override def fail[A](t: => Throwable): Identity[A] = throw t
    override def traverse[A, B](l: Iterable[A])(f: A => Identity[B]): Identity[List[B]] = l.iterator.map(f).toList
    override def traverse_[A](l: Iterable[A])(f: A => Identity[Unit]): Identity[Unit] = l.foreach(f)
    @inline private def TryWithFatal[A](r: => A): Try[A] = {
      try Success(r)
      catch { case t: Throwable => Failure(t) }
    }
    @inline private def TryNonFatal[A](r: => A): Try[A] = Try(r)
  }

}

private[quasi] sealed trait LowPriorityQuasiIOInstances extends LowPriorityQuasiIOInstances1 {

  implicit def fromBIO[F[+_, +_]](implicit F: IO2[F]): QuasiIO[F[Throwable, _]] = {
    new QuasiPrimitivesFromBIO[F, Throwable] with QuasiIO[F[Throwable, _]] {
      override final def suspendF[A](effAction: => F[Throwable, A]): F[Throwable, A] = F.suspendThrowable(effAction)
      override final def mkRef[A](a: A): F[Throwable, QuasiRef[F[Throwable, _], A]] = super[QuasiPrimitivesFromBIO].mkRef(a)
      override final def tapBothUntyped[A](eff: => F[Throwable, A])(err: Any => F[Throwable, Unit], succ: A => F[Throwable, Unit]): F[Throwable, A] = {
        super[QuasiPrimitivesFromBIO].tapBothUntyped(eff)(err, succ)
      }

      override def maybeSuspend[A](eff: => A): F[Throwable, A] = F.syncThrowable(eff)
      override def maybeSuspendEither[A](eff: => Either[Throwable, A]): F[Throwable, A] = F.fromEither(eff)
      override def definitelyRecoverUnsafeIgnoreTrace[A](action: => F[Throwable, A])(recover: Throwable => F[Throwable, A]): F[Throwable, A] = {
        F.suspendThrowable(action).sandbox.catchAll(recover apply _.toThrowable)
      }
      override def definitelyRecoverWithTrace[A](action: => F[Throwable, A])(recover: (Throwable, Exit.Trace[Throwable]) => F[Throwable, A]): F[Throwable, A] = {
        F.suspendThrowable(action).sandbox.catchAll(e => recover(e.toThrowable, e.trace))
      }
      override def redeem[A, B](action: => F[Throwable, A])(failure: Throwable => F[Throwable, B], success: A => F[Throwable, B]): F[Throwable, B] = {
        action.redeem(failure, success)
      }
      override def fail[A](t: => Throwable): F[Throwable, A] = F.fail(t)
      override def bracketCase[A, B](acquire: => F[Throwable, A])(release: (A, Option[Throwable]) => F[Throwable, Unit])(use: A => F[Throwable, B]): F[Throwable, B] = {
        F.bracketCase[Throwable, A, B](acquire = F.suspendThrowable(acquire))(release = {
          case (a, exit) =>
            exit match {
              case Exit.Success(_) => release(a, None).orTerminate
              case failure: Exit.Failure[Throwable] => release(a, Some(failure.toThrowable)).orTerminate
            }
        })(use = use)
      }
      override def guaranteeOnFailure[A](fa: => F[Throwable, A])(cleanupOnFailure: Throwable => F[Throwable, Unit]): F[Throwable, A] = {
        F.guaranteeOnFailure(F.suspendThrowable(fa), (e: Exit.Failure[Throwable]) => cleanupOnFailure(e.toThrowable).orTerminate)
      }
      override def guaranteeOnInterrupt[A](fa: => F[Throwable, A])(cleanupOnInterrupt: Exit.Trace[Nothing] => F[Throwable, Unit]): F[Throwable, A] = {
        F.guaranteeOnInterrupt(F.suspendThrowable(fa), e => cleanupOnInterrupt(e.trace).orTerminate)
      }
    }
  }

}

private[quasi] sealed trait LowPriorityQuasiIOInstances1 {

  /**
    * This instance uses 'no more orphans' trick to provide an Optional instance
    * only IFF you have cats-effect as a dependency without REQUIRING a cats-effect dependency.
    *
    * Optional instance via https://blog.7mind.io/no-more-orphans.html
    */
  implicit def fromCats[F[_], Sync[_[_]]: `cats.effect.kernel.Sync`](implicit F0: Sync[F]): QuasiIO[F] = {
    val F = F0.asInstanceOf[cats.effect.kernel.Sync[F]]
    new QuasiPrimitivesFromCats[F](F) with QuasiIO[F] {
      override final def suspendF[A](effAction: => F[A]): F[A] = super[QuasiPrimitivesFromCats].suspendF(effAction)
      override final def mkRef[A](a: A): F[QuasiRef[F, A]] = super[QuasiPrimitivesFromCats].mkRef(a)
      override final def tapBothUntyped[A](eff: => F[A])(err: Any => F[Unit], succ: A => F[Unit]): F[A] = {
        super[QuasiPrimitivesFromCats].tapBothUntyped(eff)(err, succ)
      }

      override def maybeSuspend[A](eff: => A): F[A] = F.delay(eff)
      override def maybeSuspendEither[A](eff: => Either[Throwable, A]): F[A] = F.defer(F.fromEither(eff))
      override def definitelyRecoverUnsafeIgnoreTrace[A](action: => F[A])(recover: Throwable => F[A]): F[A] = {
        F.handleErrorWith(F.defer(action))(recover)
      }
      override def definitelyRecoverWithTrace[A](action: => F[A])(recoverCause: (Throwable, Exit.Trace[Throwable]) => F[A]): F[A] = {
        definitelyRecoverUnsafeIgnoreTrace(action)(e => recoverCause(e, Exit.Trace.ThrowableTrace(e)))
      }
      override def redeem[A, B](action: => F[A])(failure: Throwable => F[B], success: A => F[B]): F[B] = {
        F.redeemWith(action)(failure, success)
      }
      override def fail[A](t: => Throwable): F[A] = F.defer(F.raiseError(t))
      override def bracketCase[A, B](acquire: => F[A])(release: (A, Option[Throwable]) => F[Unit])(use: A => F[B]): F[B] = {
        F.bracketCase(acquire = F.defer(acquire))(use = use)(release = {
          case (a, exitCase) =>
            exitCase match {
              case Outcome.Succeeded(_) => release(a, None)
              case Outcome.Errored(e) => release(a, Some(e))
              case Outcome.Canceled() => release(a, Some(new InterruptedException("cats.effect.kernel.Outcome.Canceled()")))
            }
        })
      }
      override def guaranteeOnFailure[A](fa: => F[A])(cleanupOnFailure: Throwable => F[Unit]): F[A] = {
        F.guaranteeCase(F.defer(fa)) {
          case Outcome.Succeeded(_) => F.unit
          case Outcome.Errored(e) => cleanupOnFailure(e)
          case Outcome.Canceled() => cleanupOnFailure(new InterruptedException("cats.effect.kernel.Outcome.Canceled()"))
        }
      }
      override def guaranteeOnInterrupt[A](fa: => F[A])(cleanupOnInterrupt: Exit.Trace[Nothing] => F[Unit]): F[A] = {
        F.onCancel(fa, F.defer(cleanupOnInterrupt(Exit.Trace.forThrowable(new InterruptedException("cats.effect.kernel.Outcome.Canceled()")))))
      }
    }
  }

}

/**
  * Evidence that `F` supports a subset of [[QuasiIO]] capabilities - state, non-effectful not-guaranteed suspension,
  * and setting finalizers that can't inspect the error.
  *
  * Internal use class, as with [[QuasiIO]], it's only public so that you can define your own instances,
  * better use [[izumi.functional.bio]] or [[cats]] typeclasses for application logic.
  */
trait QuasiPrimitives[F[_]] extends QuasiApplicative[F] {
  def flatMap[A, B](fa: F[A])(f: A => F[B]): F[B]

  def tailRecM[A, B](a: A)(f: A => F[Either[A, B]]): F[B] = {
    flatMap(f(a)) {
      case Left(next) => tailRecM(next)(f)
      case Right(res) => pure(res)
    }
  }

  def bracket[A, B](acquire: => F[A])(release: A => F[Unit])(use: A => F[B]): F[B]
  def guarantee[A](fa: => F[A])(`finally`: => F[Unit]): F[A] = bracket(acquire = unit)(release = _ => `finally`)(use = _ => fa)

  def uninterruptibleExcept[A](f: RestoreInterruption1[F] => F[A]): F[A]

  def mkRef[A](a: A): F[QuasiRef[F, A]]

  def suspendF[A](effAction: => F[A]): F[A]

  def traverse[A, B](l: Iterable[A])(f: A => F[B]): F[List[B]] = {
    // All reasonable effect types will be stack-safe (not heap-safe!) on left-associative flatMaps so foldLeft is ok here.
    // note: overriden in all default impls
    l.foldLeft(pure(List.empty[B])) {
      (acc, a) =>
        flatMap(acc)(list => map(f(a))(r => list ++ List(r)))
    }
  }
  def traverse_[A](l: Iterable[A])(f: A => F[Unit]): F[Unit] = {
    // All reasonable effect types will be stack-safe (not heap-safe!) on left-associative flatMaps so foldLeft is ok here.
    // note: overriden in all default impls
    l.foldLeft(unit) {
      (acc, a) =>
        flatMap(acc)(_ => f(a))
    }
  }

  def tapBothUntyped[A](eff: => F[A])(err: Any => F[Unit], succ: A => F[Unit]): F[A]
}

object QuasiPrimitives extends LowPriorityQuasiPrimitivesInstances {
  @inline def apply[F[_]: QuasiPrimitives]: QuasiPrimitives[F] = implicitly

  @inline implicit def quasiPrimitivesIdentity: QuasiPrimitives[Identity] = QuasiIOIdentity
}

private[quasi] sealed trait LowPriorityQuasiPrimitivesInstances extends LowPriorityQuasiPrimitivesInstances1 {
  implicit def fromBIO[F[+_, +_], E](implicit F: IO2[F]): QuasiPrimitives[F[E, _]] = new QuasiPrimitivesFromBIO[F, E]
}

private[quasi] sealed trait LowPriorityQuasiPrimitivesInstances1 {

  /**
    * This instance uses 'no more orphans' trick to provide an Optional instance
    * only IFF you have cats-effect as a dependency without REQUIRING a cats-effect dependency.
    *
    * Optional instance via https://blog.7mind.io/no-more-orphans.html
    */
  implicit def fromCats[F[_], Sync[_[_]]: `cats.effect.kernel.Sync`](implicit F0: Sync[F]): QuasiPrimitives[F] = {
    new QuasiPrimitivesFromCats(F0.asInstanceOf[cats.effect.kernel.Sync[F]])
  }

}

private[quasi] sealed class QuasiPrimitivesFromBIO[F[+_, +_], E](implicit F: IO2[F]) extends QuasiPrimitives[F[E, _]] {
  /** Overridden in [[LowPriorityQuasiIOInstances.fromBIO]] */
  override def suspendF[A](f: => F[E, A]): F[E, A] = F.suspendSafe(f)

  override def mkRef[A](a: A): F[E, QuasiRef[F[E, _], A]] = {
    QuasiRef.fromMaybeSuspend[F[E, _], A](a)(Morphism1(f => F.sync(f())))
  }

  override def tapBothUntyped[A](eff: => F[E, A])(err: Any => F[E, Unit], succ: A => F[E, Unit]): F[E, A] = {
    F.tapBoth(eff)(err, succ)
  }

  override final def pure[A](a: A): F[E, A] = F.pure(a)
  override final def map[A, B](fa: F[E, A])(f: A => B): F[E, B] = F.map(fa)(f)
  override final def map2[A, B, C](fa: F[E, A], fb: => F[E, B])(f: (A, B) => C): F[E, C] = F.map2(fa, fb)(f)
  override final def flatMap[A, B](fa: F[E, A])(f: A => F[E, B]): F[E, B] = F.flatMap(fa)(f)
  override final def tailRecM[A, B](a: A)(f: A => F[E, Either[A, B]]): F[E, B] = F.tailRecM(a)(f)

  override final def bracket[A, B](acquire: => F[E, A])(release: A => F[E, Unit])(use: A => F[E, B]): F[E, B] = {
    F.bracket(acquire = suspendF(acquire))(release = release(_).catchAll {
      e => F.terminate(TypedError.wrapIfNotThrowable(e))
    })(use = use)
  }
  override final def guarantee[A](fa: => F[E, A])(`finally`: => F[E, Unit]): F[E, A] = {
    F.guarantee(
      suspendF(fa),
      suspendF(`finally`).catchAll {
        e => F.terminate(TypedError.wrapIfNotThrowable(e))
      },
    )
  }
  override final def uninterruptibleExcept[A](f: RestoreInterruption1[F[E, _]] => F[E, A]): F[E, A] = {
    F.uninterruptibleExcept(restore => f(Morphism1[F[E, _], F[E, _]](g => restore(g))))
  }

  override final def traverse[A, B](l: Iterable[A])(f: A => F[E, B]): F[E, List[B]] = F.traverse(l)(f)
  override final def traverse_[A](l: Iterable[A])(f: A => F[E, Unit]): F[E, Unit] = F.traverse_(l)(f)
}

private[quasi] sealed class QuasiPrimitivesFromCats[F[_]](F: cats.effect.kernel.Sync[F]) extends QuasiPrimitives[F] {
  override def suspendF[A](effAction: => F[A]): F[A] = F.defer(effAction)

  override def tapBothUntyped[A](eff: => F[A])(err: Any => F[Unit], succ: A => F[Unit]): F[A] = {
    F.attemptTap(eff)(
      _.fold(
        e => err(e),
        v => succ(v),
      )
    )
  }

  override final def pure[A](a: A): F[A] = F.pure(a)
  override final def map[A, B](fa: F[A])(f: A => B): F[B] = F.map(fa)(f)
  override final def map2[A, B, C](fa: F[A], fb: => F[B])(f: (A, B) => C): F[C] = F.flatMap(fa)(a => F.map(fb)(f(a, _)))
  override final def flatMap[A, B](fa: F[A])(f: A => F[B]): F[B] = F.flatMap(fa)(f)
  override final def tailRecM[A, B](a: A)(f: A => F[Either[A, B]]): F[B] = F.tailRecM(a)(f)

  override final def bracket[A, B](acquire: => F[A])(release: A => F[Unit])(use: A => F[B]): F[B] = {
    F.bracket(acquire = F.defer(acquire))(use = use)(release = release)
  }
  override final def guarantee[A](fa: => F[A])(`finally`: => F[Unit]): F[A] = {
    F.guarantee(F.defer(fa), F.defer(`finally`))
  }
  override final def uninterruptibleExcept[A](f: RestoreInterruption1[F] => F[A]): F[A] = {
    F.uncancelable(restore => f(Morphism1[F, F](g => restore(g))))
  }

  override def mkRef[A](a: A): F[QuasiRef[F, A]] = {
    QuasiRef.fromMaybeSuspend(a)(Morphism1(f => F.delay(f())))
  }

  override final def traverse[A, B](l: Iterable[A])(f: A => F[B]): F[List[B]] = cats.instances.list.catsStdInstancesForList.traverse(l.toList)(f)(using F)
  override final def traverse_[A](l: Iterable[A])(f: A => F[Unit]): F[Unit] = cats.instances.list.catsStdInstancesForList.traverse_(l.toList)(f)(using F)
}

/**
  * An `Applicative` capability for `F`. Unlike `QuasiIO` there's nothing "quasi" about it – it makes sense. But named like that for consistency anyway.
  *
  * Internal use class, as with [[QuasiIO]], it's only public so that you can define your own instances,
  * better use [[izumi.functional.bio]] or [[cats]] typeclasses for application logic.
  */
trait QuasiApplicative[F[_]] extends QuasiFunctor[F] {
  def pure[A](a: A): F[A]
  def map2[A, B, C](fa: F[A], fb: => F[B])(f: (A, B) => C): F[C]

  def traverse[A, B](l: Iterable[A])(f: A => F[B]): F[List[B]]
  def traverse_[A](l: Iterable[A])(f: A => F[Unit]): F[Unit]

  final val unit: F[Unit] = pure(())

  final def when(cond: Boolean)(ifTrue: => F[Unit]): F[Unit] = if (cond) ifTrue else unit
  final def unless(cond: Boolean)(ifFalse: => F[Unit]): F[Unit] = if (cond) unit else ifFalse
  final def ifThenElse[A](cond: Boolean)(ifTrue: => F[A], ifFalse: => F[A]): F[A] = if (cond) ifTrue else ifFalse
}

object QuasiApplicative extends LowPriorityQuasiApplicativeInstances {
  @inline def apply[F[_]: QuasiApplicative]: QuasiApplicative[F] = implicitly

  @inline implicit def quasiApplicativeIdentity: QuasiApplicative[Identity] = QuasiIOIdentity
}

private[quasi] sealed trait LowPriorityQuasiApplicativeInstances extends LowPriorityQuasiApplicativeInstances1 {
  implicit def fromBIO[F[+_, +_], E](implicit F: Applicative2[F]): QuasiApplicative[F[E, _]] = {
    new QuasiApplicative[F[E, _]] {
      override def pure[A](a: A): F[E, A] = F.pure(a)
      override def map[A, B](fa: F[E, A])(f: A => B): F[E, B] = F.map(fa)(f)
      override def map2[A, B, C](fa: F[E, A], fb: => F[E, B])(f: (A, B) => C): F[E, C] = F.map2(fa, fb)(f)
      override def traverse[A, B](l: Iterable[A])(f: A => F[E, B]): F[E, List[B]] = F.traverse(l)(f)
      override def traverse_[A](l: Iterable[A])(f: A => F[E, Unit]): F[E, Unit] = F.traverse_(l)(f)
    }
  }
}

private[quasi] sealed trait LowPriorityQuasiApplicativeInstances1 {
  /**
    * This instance uses 'no more orphans' trick to provide an Optional instance
    * only IFF you have cats-core as a dependency without REQUIRING a cats-core dependency.
    *
    * Optional instance via https://blog.7mind.io/no-more-orphans.html
    */
  implicit def fromCats[F[_], Applicative[_[_]]: `cats.Applicative`](implicit F0: Applicative[F]): QuasiApplicative[F] = {
    val F = F0.asInstanceOf[cats.Applicative[F]]
    new QuasiApplicative[F] {
      override def pure[A](a: A): F[A] = F.pure(a)
      override def map[A, B](fa: F[A])(f: A => B): F[B] = F.map(fa)(f)
      override def map2[A, B, C](fa: F[A], fb: => F[B])(f: (A, B) => C): F[C] = F.map2(fa, fb)(f)
      override def traverse[A, B](l: Iterable[A])(f: A => F[B]): F[List[B]] = cats.instances.list.catsStdInstancesForList.traverse(l.toList)(f)(using F)
      override def traverse_[A](l: Iterable[A])(f: A => F[Unit]): F[Unit] = cats.instances.list.catsStdInstancesForList.traverse_(l.toList)(f)(using F)
    }
  }
}

/**
  * A `Functor` capability for `F`. Unlike `QuasiIO` there's nothing "quasi" about it – it makes sense. But named like that for consistency anyway.
  *
  * Internal use class, as with [[QuasiIO]], it's only public so that you can define your own instances,
  * better use [[izumi.functional.bio]] or [[cats]] typeclasses for application logic.
  */
trait QuasiFunctor[F[_]] {
  def map[A, B](fa: F[A])(f: A => B): F[B]
  final def widen[A, B >: A](fa: F[A]): F[B] = fa.asInstanceOf[F[B]]
}

object QuasiFunctor extends LowPriorityQuasiFunctorInstances {
  @inline def apply[F[_]: QuasiFunctor]: QuasiFunctor[F] = implicitly

  @inline implicit def quasiFunctorIdentity: QuasiFunctor[Identity] = {
    // FIXME: This instance's type is QuasiFunctor not QuasiApplicative to Scala 3 bug https://github.com/lampepfl/dotty/issues/16431
    QuasiIOIdentity
  }
}

private[quasi] sealed trait LowPriorityQuasiFunctorInstances extends LowPriorityQuasiFunctorInstances1 {
  implicit def fromBIO[F[+_, +_], E](implicit F: Functor2[F]): QuasiFunctor[F[E, _]] = {
    new QuasiFunctor[F[E, _]] {
      override def map[A, B](fa: F[E, A])(f: A => B): F[E, B] = F.map(fa)(f)
    }
  }
}

private[quasi] sealed trait LowPriorityQuasiFunctorInstances1 {
  /**
    * This instance uses 'no more orphans' trick to provide an Optional instance
    * only IFF you have cats-core as a dependency without REQUIRING a cats-core dependency.
    *
    * Optional instance via https://blog.7mind.io/no-more-orphans.html
    */
  implicit def fromCats[F[_], Functor[_[_]]: `cats.Functor`](implicit F0: Functor[F]): QuasiFunctor[F] = {
    val F = F0.asInstanceOf[cats.Functor[F]]
    new QuasiFunctor[F] {
      override def map[A, B](fa: F[A])(f: A => B): F[B] = F.map(fa)(f)
    }
  }
}

trait QuasiRef[F[_], A] {
  def get: F[A]
  def set(a: A): F[Unit]
  def update(f: A => A): F[Unit]
}

object QuasiRef {
  def mk[F[_], A](a: A)(implicit F: QuasiPrimitives[F]): F[QuasiRef[F, A]] = F.mkRef(a)

  def fromMaybeSuspend[F[_], A](a: A)(maybeSuspend: Morphism1[() => _, F]): F[QuasiRef[F, A]] = {
    maybeSuspend {
      () =>
        val ref = new AtomicReference[A](a)
        new QuasiRef[F, A] {
          override def get: F[A] = maybeSuspend(() => ref.get())
          override def set(a: A): F[Unit] = maybeSuspend(() => ref.set(a))
          override def update(f: A => A): F[Unit] = {
            maybeSuspend {
              () =>
                // can't use `.updateAndGet` because of Scala.js
                var oldValue = ref.get()
                while (!ref.compareAndSet(oldValue, f(oldValue))) {
                  oldValue = ref.get()
                }
            }
          }
        }
    }
  }
}
