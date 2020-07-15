package izumi.distage.model.effect

import cats.effect.ExitCase
import izumi.distage.model.effect.LowPriorityDIApplicativeInstances._Applicative
import izumi.distage.model.effect.LowPriorityDIEffectInstances._Sync
import izumi.functional.bio.{BIO, BIOApplicative, BIOExit}
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.language.unused

import scala.language.implicitConversions
import scala.util.{Failure, Success, Try}

trait DIEffect[F[_]] extends DIApplicative[F] {
  def flatMap[A, B](fa: F[A])(f: A => F[B]): F[B]
  def bracket[A, B](acquire: => F[A])(release: A => F[Unit])(use: A => F[B]): F[B]
  def bracketCase[A, B](acquire: => F[A])(release: (A, Option[Throwable]) => F[Unit])(use: A => F[B]): F[B]
  final def bracketAuto[A <: AutoCloseable, B](acquire: => F[A])(use: A => F[B]): F[B] = bracket(acquire)(a => maybeSuspend(a.close()))(use)

  /** A weaker version of `delay`. Does not guarantee _actual_
    * suspension of side-effects, because DIEffect[Identity] is allowed
    */
  def maybeSuspend[A](eff: => A): F[A]

  /** A stronger version of `handleErrorWith`, the difference is that
    * this will _also_ intercept Throwable defects in `ZIO`, not only typed errors
    */
  def definitelyRecover[A](action: => F[A])(recover: Throwable => F[A]): F[A]

  /** `definitelyRecover`, but the second argument is a callback that when called,
    * will return another Throwable, possible enhanced with the effect's own debugging information.
    * NOTE: the callback may perform side-effects to the original Throwable argument on the left,
    * the left throwable should be DISCARDED after calling the callback.
    * (e.g. in case of `ZIO`, the callback will mutate the throwable and attach a ZIO Trace to it.)
    */
  def definitelyRecoverCause[A](action: => F[A])(recoverCause: (Throwable, (() => Throwable)) => F[A]): F[A]

  def fail[A](t: => Throwable): F[A]

  final def widen[A, B >: A](fa: F[A]): F[B] = fa.asInstanceOf[F[B]]
  final def suspendF[A](effAction: => F[A]): F[A] = flatMap(maybeSuspend(effAction))(identity)
  final def traverse_[A](l: Iterable[A])(f: A => F[Unit]): F[Unit] = {
    // All reasonable effect types will be stack-safe (not heap-safe!) on left-associative
    // flatMaps so foldLeft is ok here. It also enables impure Identity to work correctly
    l.foldLeft(unit) {
      (acc, a) =>
        flatMap(acc)(_ => f(a))
    }
  }
}

object DIEffect extends LowPriorityDIEffectInstances {
  @inline def apply[F[_]: DIEffect]: DIEffect[F] = implicitly

  object syntax {
    implicit def suspendedSyntax[F[_], A](fa: => F[A]): DIEffectSuspendedSyntax[F, A] = new DIEffectSuspendedSyntax(() => fa)

    implicit final class DIEffectSyntax[F[_], A](private val fa: F[A]) extends AnyVal {
      @inline def map[B](f: A => B)(implicit F: DIEffect[F]): F[B] = F.map(fa)(f)
      @inline def flatMap[B](f: A => F[B])(implicit F: DIEffect[F]): F[B] = F.flatMap(fa)(f)
    }

    final class DIEffectSuspendedSyntax[F[_], A](private val fa: () => F[A]) extends AnyVal {
      @inline def guarantee(`finally`: => F[Unit])(implicit F: DIEffect[F]): F[A] = {
        F.bracket(acquire = F.unit)(release = _ => `finally`)(use = _ => fa())
      }
    }
  }

  implicit val diEffectIdentity: DIEffect[Identity] = new DIEffect[Identity] {
    override def pure[A](a: A): Identity[A] = a
    override def map[A, B](fa: Identity[A])(f: A => B): Identity[B] = f(fa)
    override def map2[A, B, C](fa: Identity[A], fb: => Identity[B])(f: (A, B) => C): Identity[C] = f(fa, fb)
    override def flatMap[A, B](a: A)(f: A => Identity[B]): Identity[B] = f(a)

    override def maybeSuspend[A](eff: => A): Identity[A] = eff
    override def definitelyRecover[A](fa: => Identity[A])(recover: Throwable => Identity[A]): Identity[A] = {
      try fa
      catch {
        case t: Throwable => recover(t)
      }
    }
    override def definitelyRecoverCause[A](action: => Identity[A])(recoverCause: (Throwable, (() => Throwable)) => Identity[A]): Identity[A] = {
      definitelyRecover(action)(e => recoverCause(e, () => e))
    }
    override def bracket[A, B](acquire: => Identity[A])(release: A => Identity[Unit])(use: A => Identity[B]): Identity[B] = {
      val a = acquire
      try use(a)
      finally release(a)
    }
    override def bracketCase[A, B](acquire: => Identity[A])(release: (A, Option[Throwable]) => Identity[Unit])(use: A => Identity[B]): Identity[B] = {
      val a = acquire
      Try(use(a)) match {
        case Failure(exception) =>
          release(a, Some(exception))
          throw exception
        case Success(value) =>
          release(a, None)
          value
      }
    }
    override def fail[A](t: => Throwable): Identity[A] = throw t
  }

  implicit def fromBIO[F[+_, +_]](implicit F: BIO[F]): DIEffect[F[Throwable, ?]] = {
    type E = Throwable
    new DIEffect[F[Throwable, ?]] {
      override def pure[A](a: A): F[E, A] = F.pure(a)
      override def map[A, B](fa: F[E, A])(f: A => B): F[E, B] = F.map(fa)(f)
      override def map2[A, B, C](fa: F[E, A], fb: => F[E, B])(f: (A, B) => C): F[E, C] = F.map2(fa, fb)(f)
      override def flatMap[A, B](fa: F[E, A])(f: A => F[E, B]): F[E, B] = F.flatMap(fa)(f)

      override def maybeSuspend[A](eff: => A): F[E, A] = F.syncThrowable(eff)
      override def definitelyRecover[A](action: => F[E, A])(recover: Throwable => F[E, A]): F[E, A] = {
        suspendF(action).sandbox.catchAll(recover apply _.toThrowable)
      }
      override def definitelyRecoverCause[A](action: => F[Throwable, A])(recover: (Throwable, () => Throwable) => F[Throwable, A]): F[Throwable, A] = {
        suspendF(action).sandbox.catchAll(e => recover(e.toThrowable, () => e.trace.unsafeAttachTrace(identity)))
      }

      override def fail[A](t: => Throwable): F[Throwable, A] = F.fail(t)
      override def bracket[A, B](acquire: => F[E, A])(release: A => F[E, Unit])(use: A => F[E, B]): F[E, B] = {
        F.bracket(acquire = suspendF(acquire))(release = release(_).orTerminate)(use = use)
      }
      override def bracketCase[A, B](acquire: => F[E, A])(release: (A, Option[E]) => F[E, Unit])(use: A => F[E, B]): F[E, B] = {
        F.bracketCase[Any, Throwable, A, B](acquire = suspendF(acquire))(release = {
          case (a, exit) =>
            exit match {
              case BIOExit.Success(_) => release(a, None).orTerminate
              case failure: BIOExit.Failure[E] => release(a, Some(failure.toThrowable)).orTerminate
            }
        })(use = use)
      }
    }
  }
}

private[effect] sealed trait LowPriorityDIEffectInstances {

  /**
    * This instance uses 'no more orphans' trick to provide an Optional instance
    * only IFF you have cats-effect as a dependency without REQUIRING a cats-effect dependency.
    *
    * Optional instance via https://blog.7mind.io/no-more-orphans.html
    */
  implicit def fromCatsEffect[F[_], R[_[_]]](implicit @unused l: _Sync[R], F0: R[F]): DIEffect[F] = {
    val F = F0.asInstanceOf[cats.effect.Sync[F]]
    new DIEffect[F] {
      override def pure[A](a: A): F[A] = F.pure(a)
      override def map[A, B](fa: F[A])(f: A => B): F[B] = F.map(fa)(f)
      override def map2[A, B, C](fa: F[A], fb: => F[B])(f: (A, B) => C): F[C] = F.flatMap(fa)(a => F.map(fb)(f(a, _)))
      override def flatMap[A, B](fa: F[A])(f: A => F[B]): F[B] = F.flatMap(fa)(f)

      override def maybeSuspend[A](eff: => A): F[A] = F.delay(eff)
      override def definitelyRecover[A](action: => F[A])(recover: Throwable => F[A]): F[A] = {
        F.handleErrorWith(F.suspend(action))(recover)
      }
      override def definitelyRecoverCause[A](action: => F[A])(recoverCause: (Throwable, () => Throwable) => F[A]): F[A] = {
        definitelyRecover(action)(e => recoverCause(e, () => e))
      }
      override def fail[A](t: => Throwable): F[A] = F.suspend(F.raiseError(t))
      override def bracket[A, B](acquire: => F[A])(release: A => F[Unit])(use: A => F[B]): F[B] = {
        F.bracket(acquire = F.suspend(acquire))(use = use)(release = release)
      }
      override def bracketCase[A, B](acquire: => F[A])(release: (A, Option[Throwable]) => F[Unit])(use: A => F[B]): F[B] = {
        F.bracketCase(acquire = F.suspend(acquire))(use = use)(release = {
          case (a, exitCase) =>
            exitCase match {
              case ExitCase.Completed => release(a, None)
              case ExitCase.Error(e) => release(a, Some(e))
              case ExitCase.Canceled => release(a, Some(new InterruptedException))
            }
        })
      }
    }
  }

}

object LowPriorityDIEffectInstances {
  /**
    * This instance uses 'no more orphans' trick to provide an Optional instance
    * only IFF you have cats-effect as a dependency without REQUIRING a cats-effect dependency.
    *
    * Optional instance via https://blog.7mind.io/no-more-orphans.html
    */
  sealed abstract class _Sync[R[_[_]]]
  object _Sync {
    @inline implicit final def catsEffectSync: _Sync[cats.effect.Sync] = null
  }
}

trait DIApplicative[F[_]] {
  def pure[A](a: A): F[A]
  def map[A, B](fa: F[A])(f: A => B): F[B]
  def map2[A, B, C](fa: F[A], fb: => F[B])(f: (A, B) => C): F[C]
  final val unit: F[Unit] = pure(())
}

object DIApplicative extends LowPriorityDIApplicativeInstances {
  @inline def apply[F[_]: DIApplicative]: DIApplicative[F] = implicitly

  implicit val diapplicativeIdentity: DIApplicative[Identity] = new DIApplicative[Identity] {
    override def pure[A](a: A): Identity[A] = a
    override def map[A, B](fa: Identity[A])(f: A => B): Identity[B] = f(fa)
    override def map2[A, B, C](fa: Identity[A], fb: => Identity[B])(f: (A, B) => C): Identity[C] = f(fa, fb)
  }

  implicit def fromBIO[F[+_, +_], E](implicit F: BIOApplicative[F]): DIApplicative[F[E, ?]] = {
    new DIApplicative[F[E, ?]] {
      override def pure[A](a: A): F[E, A] = F.pure(a)
      override def map[A, B](fa: F[E, A])(f: A => B): F[E, B] = F.map(fa)(f)
      override def map2[A, B, C](fa: F[E, A], fb: => F[E, B])(f: (A, B) => C): F[E, C] = F.map2(fa, fb)(f)
    }
  }
}

trait LowPriorityDIApplicativeInstances {
  /**
    * This instance uses 'no more orphans' trick to provide an Optional instance
    * only IFF you have cats-core as a dependency without REQUIRING a cats-core dependency.
    *
    * Optional instance via https://blog.7mind.io/no-more-orphans.html
    */
  implicit def fromCats[F[_], R[_[_]]](implicit @unused R: _Applicative[R], F0: R[F]): DIApplicative[F] = {
    val F = F0.asInstanceOf[cats.Applicative[F]]
    new DIApplicative[F] {
      override def pure[A](a: A): F[A] = F.pure(a)
      override def map[A, B](fa: F[A])(f: A => B): F[B] = F.map(fa)(f)
      override def map2[A, B, C](fa: F[A], fb: => F[B])(f: (A, B) => C): F[C] = F.map2(fa, fb)(f)
    }
  }
}

object LowPriorityDIApplicativeInstances {
  /**
    * This instance uses 'no more orphans' trick to provide an Optional instance
    * only IFF you have cats-core as a dependency without REQUIRING a cats-core dependency.
    *
    * Optional instance via https://blog.7mind.io/no-more-orphans.html
    */
  sealed abstract class _Applicative[R[_[_]]]
  object _Applicative {
    @inline implicit final def catsApplicative: _Applicative[cats.Applicative] = null
  }
}
