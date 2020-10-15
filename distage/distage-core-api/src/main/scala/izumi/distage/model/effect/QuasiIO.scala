package izumi.distage.model.effect

import cats.effect.ExitCase
import izumi.functional.bio.{BIO, BIOApplicative, BIOExit}
import izumi.fundamentals.orphans.{`cats.Applicative`, `cats.effect.Sync`}
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.language.unused

import scala.language.implicitConversions
import scala.util.{Failure, Success, Try}

/**
  * Evidence that `F` is _almost_ `IO`-monad-like capabilities, but not quite,
  * because we also allow an impure [[izumi.fundamentals.platform.functional.Identity]] instance,
  * for which `maybeSuspend` does not in fact suspend!
  *
  * If you use this interface and forget to add manual suspension with by-name's and Function1's,
  * you're going to get weird behavior for Identity instance.
  *
  * This interface serves internal need of `distage` for interoperability with all the existing
  * Scala effect types and also impure `Identity`, you should NOT refer to it in your code if possible,
  * it is public because you may want to define your own instances if a suitable instance of [[izumi.distage.modules.DefaultModule]]
  * is missing for your custom effect type. Better use [[izumi.functional.bio]] or [[cats]] typeclasses for application logic.
  *
  * @see [[izumi.distage.modules.DefaultModule]] - `DefaultModule` makes instances of `QuasiIO` for cats-effect, ZIO,
  *      monix, monix-bio, `Identity`, and others, available for summoning in your wiring automatically
  */
trait QuasiIO[F[_]] extends QuasiApplicative[F] {
  def flatMap[A, B](fa: F[A])(f: A => F[B]): F[B]

  def guarantee[A](fa: => F[A])(`finally`: => F[Unit]): F[A] = bracket(acquire = unit)(release = _ => `finally`)(use = _ => fa)
  def bracket[A, B](acquire: => F[A])(release: A => F[Unit])(use: A => F[B]): F[B]
  def bracketCase[A, B](acquire: => F[A])(release: (A, Option[Throwable]) => F[Unit])(use: A => F[B]): F[B]
  final def bracketAuto[A <: AutoCloseable, B](acquire: => F[A])(use: A => F[B]): F[B] = bracket(acquire)(a => maybeSuspend(a.close()))(use)

  /** A weaker version of `delay`. Does not guarantee _actual_
    * suspension of side-effects, because QuasiIO[Identity] is allowed
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

  def suspendF[A](effAction: => F[A]): F[A] = {
    flatMap(maybeSuspend(effAction))(identity)
  }
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
}

object QuasiIO extends LowPriorityQuasiIOInstances {
  @inline def apply[F[_]: QuasiIO]: QuasiIO[F] = implicitly

  object syntax {
    implicit def suspendedSyntax[F[_], A](fa: => F[A]): QuasiIOSuspendedSyntax[F, A] = new QuasiIOSuspendedSyntax(() => fa)

    implicit final class QuasiIOSyntax[F[_], A](private val fa: F[A]) extends AnyVal {
      @inline def map[B](f: A => B)(implicit F: QuasiIO[F]): F[B] = F.map(fa)(f)
      @inline def flatMap[B](f: A => F[B])(implicit F: QuasiIO[F]): F[B] = F.flatMap(fa)(f)
    }

    final class QuasiIOSuspendedSyntax[F[_], A](private val fa: () => F[A]) extends AnyVal {
      @inline def guarantee(`finally`: => F[Unit])(implicit F: QuasiIO[F]): F[A] = {
        F.bracket(acquire = F.unit)(release = _ => `finally`)(use = _ => fa())
      }
    }
  }

  implicit val QuasiIOIdentity: QuasiIO[Identity] = new QuasiIO[Identity] {
    override def pure[A](a: A): Identity[A] = a
    override def map[A, B](fa: Identity[A])(f: A => B): Identity[B] = f(fa)
    override def map2[A, B, C](fa: Identity[A], fb: => Identity[B])(f: (A, B) => C): Identity[C] = f(fa, fb)
    override def flatMap[A, B](a: A)(f: A => Identity[B]): Identity[B] = f(a)

    override def maybeSuspend[A](eff: => A): Identity[A] = eff
    override def suspendF[A](effAction: => A): Identity[A] = effAction
    override def definitelyRecover[A](fa: => Identity[A])(recover: Throwable => Identity[A]): Identity[A] = {
      try { fa }
      catch { case t: Throwable => recover(t) }
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
    override def guarantee[A](fa: => Identity[A])(`finally`: => Identity[Unit]): Identity[A] = {
      try fa
      finally `finally`
    }
    override def fail[A](t: => Throwable): Identity[A] = throw t
    override def traverse[A, B](l: Iterable[A])(f: A => Identity[B]): Identity[List[B]] = l.iterator.map(f).toList
    override def traverse_[A](l: Iterable[A])(f: A => Identity[Unit]): Identity[Unit] = l.foreach(f)
  }

  implicit def fromBIO[F[+_, +_]](implicit F: BIO[F]): QuasiIO[F[Throwable, ?]] = {
    type E = Throwable
    new QuasiIO[F[Throwable, ?]] {
      override def pure[A](a: A): F[E, A] = F.pure(a)
      override def map[A, B](fa: F[E, A])(f: A => B): F[E, B] = F.map(fa)(f)
      override def map2[A, B, C](fa: F[E, A], fb: => F[E, B])(f: (A, B) => C): F[E, C] = F.map2(fa, fb)(f)
      override def flatMap[A, B](fa: F[E, A])(f: A => F[E, B]): F[E, B] = F.flatMap(fa)(f)

      override def maybeSuspend[A](eff: => A): F[E, A] = F.syncThrowable(eff)
      override def suspendF[A](effAction: => F[Throwable, A]): F[Throwable, A] = F.suspend(effAction)
      override def definitelyRecover[A](action: => F[E, A])(recover: Throwable => F[E, A]): F[E, A] = {
        F.suspend(action).sandbox.catchAll(recover apply _.toThrowable)
      }
      override def definitelyRecoverCause[A](action: => F[Throwable, A])(recover: (Throwable, () => Throwable) => F[Throwable, A]): F[Throwable, A] = {
        F.suspend(action).sandbox.catchAll(e => recover(e.toThrowable, () => e.trace.unsafeAttachTrace(identity)))
      }

      override def fail[A](t: => Throwable): F[Throwable, A] = F.fail(t)
      override def bracket[A, B](acquire: => F[E, A])(release: A => F[E, Unit])(use: A => F[E, B]): F[E, B] = {
        F.bracket(acquire = F.suspend(acquire))(release = release(_).orTerminate)(use = use)
      }
      override def bracketCase[A, B](acquire: => F[E, A])(release: (A, Option[E]) => F[E, Unit])(use: A => F[E, B]): F[E, B] = {
        F.bracketCase[Any, Throwable, A, B](acquire = F.suspend(acquire))(release = {
          case (a, exit) =>
            exit match {
              case BIOExit.Success(_) => release(a, None).orTerminate
              case failure: BIOExit.Failure[E] => release(a, Some(failure.toThrowable)).orTerminate
            }
        })(use = use)
      }
      override def guarantee[A](fa: => F[Throwable, A])(`finally`: => F[Throwable, Unit]): F[Throwable, A] = {
        F.guarantee(F.suspend(fa), F.suspend(`finally`).orTerminate)
      }
      override def traverse[A, B](l: Iterable[A])(f: A => F[E, B]): F[E, List[B]] = F.traverse(l)(f)
      override def traverse_[A](l: Iterable[A])(f: A => F[E, Unit]): F[E, Unit] = F.traverse_(l)(f)
    }
  }
}

private[effect] sealed trait LowPriorityQuasiIOInstances {

  /**
    * This instance uses 'no more orphans' trick to provide an Optional instance
    * only IFF you have cats-effect as a dependency without REQUIRING a cats-effect dependency.
    *
    * Optional instance via https://blog.7mind.io/no-more-orphans.html
    */
  implicit def fromCats[F[_], Sync[_[_]]](implicit @unused l: `cats.effect.Sync`[Sync], F0: Sync[F]): QuasiIO[F] = {
    val F = F0.asInstanceOf[cats.effect.Sync[F]]
    new QuasiIO[F] {
      override def pure[A](a: A): F[A] = F.pure(a)
      override def map[A, B](fa: F[A])(f: A => B): F[B] = F.map(fa)(f)
      override def map2[A, B, C](fa: F[A], fb: => F[B])(f: (A, B) => C): F[C] = F.flatMap(fa)(a => F.map(fb)(f(a, _)))
      override def flatMap[A, B](fa: F[A])(f: A => F[B]): F[B] = F.flatMap(fa)(f)

      override def maybeSuspend[A](eff: => A): F[A] = F.delay(eff)
      override def suspendF[A](effAction: => F[A]): F[A] = F.suspend(effAction)
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
      override def guarantee[A](fa: => F[A])(`finally`: => F[Unit]): F[A] = {
        F.guarantee(F.suspend(fa))(F.suspend(`finally`))
      }
      override def traverse[A, B](l: Iterable[A])(f: A => F[B]): F[List[B]] = cats.instances.list.catsStdInstancesForList.traverse(l.toList)(f)(F)
      override def traverse_[A](l: Iterable[A])(f: A => F[Unit]): F[Unit] = cats.instances.list.catsStdInstancesForList.traverse_(l.toList)(f)(F)
    }
  }

}

/**
  * An `Applicative` capability for `F`. Unlike `QuasiIO` there's nothing "quasi" about it – it makes sense. But named like that for consistency anyway.
  *
  * Internal use class, as with [[QuasiIO]], it's only public so that you can define your own instances,
  * better use [[izumi.functional.bio]] or [[cats]] typeclasses for application logic.
  */
trait QuasiApplicative[F[_]] {
  def pure[A](a: A): F[A]

  def map[A, B](fa: F[A])(f: A => B): F[B]
  def map2[A, B, C](fa: F[A], fb: => F[B])(f: (A, B) => C): F[C]

  def traverse[A, B](l: Iterable[A])(f: A => F[B]): F[List[B]]
  def traverse_[A](l: Iterable[A])(f: A => F[Unit]): F[Unit]

  final def widen[A, B >: A](fa: F[A]): F[B] = fa.asInstanceOf[F[B]]
  final val unit: F[Unit] = pure(())
}

object QuasiApplicative extends LowPriorityQuasiApplicativeInstances {
  @inline def apply[F[_]: QuasiApplicative]: QuasiApplicative[F] = implicitly

  implicit val QuasiApplicativeIdentity: QuasiApplicative[Identity] = new QuasiApplicative[Identity] {
    override def pure[A](a: A): Identity[A] = a
    override def map[A, B](fa: Identity[A])(f: A => B): Identity[B] = f(fa)
    override def map2[A, B, C](fa: Identity[A], fb: => Identity[B])(f: (A, B) => C): Identity[C] = f(fa, fb)
    override def traverse[A, B](l: Iterable[A])(f: A => Identity[B]): Identity[List[B]] = l.iterator.map(f).toList
    override def traverse_[A](l: Iterable[A])(f: A => Identity[Unit]): Identity[Unit] = l.foreach(f)
  }

  implicit def fromBIO[F[+_, +_], E](implicit F: BIOApplicative[F]): QuasiApplicative[F[E, ?]] = {
    new QuasiApplicative[F[E, ?]] {
      override def pure[A](a: A): F[E, A] = F.pure(a)
      override def map[A, B](fa: F[E, A])(f: A => B): F[E, B] = F.map(fa)(f)
      override def map2[A, B, C](fa: F[E, A], fb: => F[E, B])(f: (A, B) => C): F[E, C] = F.map2(fa, fb)(f)
      override def traverse[A, B](l: Iterable[A])(f: A => F[E, B]): F[E, List[B]] = F.traverse(l)(f)
      override def traverse_[A](l: Iterable[A])(f: A => F[E, Unit]): F[E, Unit] = F.traverse_(l)(f)
    }
  }
}

trait LowPriorityQuasiApplicativeInstances {
  /**
    * This instance uses 'no more orphans' trick to provide an Optional instance
    * only IFF you have cats-core as a dependency without REQUIRING a cats-core dependency.
    *
    * Optional instance via https://blog.7mind.io/no-more-orphans.html
    */
  implicit def fromCats[F[_], Applicative[_[_]]](implicit @unused R: `cats.Applicative`[Applicative], F0: Applicative[F]): QuasiApplicative[F] = {
    val F = F0.asInstanceOf[cats.Applicative[F]]
    new QuasiApplicative[F] {
      override def pure[A](a: A): F[A] = F.pure(a)
      override def map[A, B](fa: F[A])(f: A => B): F[B] = F.map(fa)(f)
      override def map2[A, B, C](fa: F[A], fb: => F[B])(f: (A, B) => C): F[C] = F.map2(fa, fb)(f)
      override def traverse[A, B](l: Iterable[A])(f: A => F[B]): F[List[B]] = cats.instances.list.catsStdInstancesForList.traverse(l.toList)(f)(F)
      override def traverse_[A](l: Iterable[A])(f: A => F[Unit]): F[Unit] = cats.instances.list.catsStdInstancesForList.traverse_(l.toList)(f)(F)
    }
  }
}
