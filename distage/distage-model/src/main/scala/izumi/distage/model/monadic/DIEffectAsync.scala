package izumi.distage.model.monadic

import cats.Parallel
import cats.effect.Timer
import izumi.distage.model.monadic.LowPriorityDIEffectAsyncInstances.{_Parallel, _Timer}
import izumi.functional.bio.{BIOAsync, F}
import izumi.fundamentals.platform.functional.Identity

import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.concurrent.{Await, ExecutionContext, Future}

trait DIEffectAsync[F[_]] {
  def parTraverse_[A](l: Iterable[A])(f: A => F[Unit]): F[Unit]
  def sleep(duration: FiniteDuration): F[Unit]
}

object DIEffectAsync extends LowPriorityDIEffectAsyncInstances {
  def apply[F[_]: DIEffectAsync]: DIEffectAsync[F] = implicitly

  implicit val diEffectParIdentity: DIEffectAsync[Identity] = {
    new DIEffectAsync[Identity] {
      override def parTraverse_[A](l: Iterable[A])(f: A => Unit): Unit = {
        implicit val ec = ExecutionContext.global
        val future = Future.sequence(l.map(a => Future(f(a))))
        Await.result(future, Duration.Inf)
      }
      override def sleep(duration: FiniteDuration): Identity[Unit] = {
        Thread.sleep(duration.toMillis)
      }
    }
  }

  implicit def fromBIOAsync[F[+_, +_]: BIOAsync]: DIEffectAsync[F[Throwable, ?]] = {
    new DIEffectAsync[F[Throwable, ?]] {
      override def parTraverse_[A](l: Iterable[A])(f: A => F[Throwable, Unit]): F[Throwable, Unit] = {
        F.parTraverse_(l)(f).void
      }
      override def sleep(duration: FiniteDuration): F[Throwable, Unit] = {
        F.sleep(duration)
      }
    }
  }

}

private[monadic] sealed trait LowPriorityDIEffectAsyncInstances {
  /**
    * This instance uses 'no more orphans' trick to provide an Optional instance
    * only IFF you have cats-effect as a dependency without REQUIRING a cats-effect dependency.
    *
    * Optional instance via https://blog.7mind.io/no-more-orphans.html
    */
  implicit final def fromParallelTimer[F[_], P[_[_]]: _Parallel, T[_[_]]: _Timer](implicit P: P[F], T: T[F]): DIEffectAsync[F] = {
    new DIEffectAsync[F] {
      override def parTraverse_[A](l: Iterable[A])(f: A => F[Unit]): F[Unit] = {
        Parallel.parTraverse_(l.toList)(f)(cats.instances.list.catsStdInstancesForList, P.asInstanceOf[Parallel[F]])
      }
      override def sleep(duration: FiniteDuration): F[Unit] = {
        T.asInstanceOf[Timer[F]].sleep(duration)
      }
    }
  }
}

private object LowPriorityDIEffectAsyncInstances {
  sealed trait _Parallel[K[_[_]]]
  object _Parallel {
    @inline implicit final def get: _Parallel[cats.Parallel] = null
  }

  sealed trait _Timer[K[_[_]]]
  object _Timer {
    @inline implicit final def get: _Timer[cats.effect.Timer] = null
  }
}
