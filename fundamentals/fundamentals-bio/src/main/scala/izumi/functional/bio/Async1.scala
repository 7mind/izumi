package izumi.functional.bio

import izumi.fundamentals.orphans.{`cats.effect.kernel.Async`, `cats.effect.kernel.GenTemporal`}
import izumi.fundamentals.platform.functional.Identity

import scala.collection.compat.*
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

/**
  * Parallel & async operations for `F` required by `distage-*` libraries.
  * Unlike `IO1` there's nothing "quasi" about it – it makes sense. But named like that for consistency anyway.
  *
  * Internal use class, as with [[IO1]], it's only public so that you can define your own instances,
  * better use [[izumi.functional.bio]] or [[cats]] typeclasses for application logic.
  *
  * TODO: we want to get rid of this by providing Identity implementations for Parallel3, Async3 and Temporal3
  * See https://github.com/7mind/izumi/issues/787
  */
trait Async1[F[_]] {
  def async[A](effect: (Either[Throwable, A] => Unit) => Unit): F[A]
  def fromFuture[A](effect: => Future[A]): F[A]
  def parTraverse[A, B](l: IterableOnce[A])(f: A => F[B]): F[List[B]]
  def parTraverse_[A](l: IterableOnce[A])(f: A => F[Unit]): F[Unit]
  def parTraverseN[A, B](n: Int)(l: IterableOnce[A])(f: A => F[B]): F[List[B]]
  def parTraverseN_[A](n: Int)(l: IterableOnce[A])(f: A => F[Unit]): F[Unit]
}

object Async1 extends LowPriorityAsync1Instances {
  def apply[F[_]: Async1]: Async1[F] = implicitly

  implicit lazy val async1Identity: Async1[Identity] = __Async1PlatformSpecific.async1Identity

  implicit def fromBIO[F[+_, +_]](implicit F: WeakAsync2[F]): Async1[F[Throwable, _]] = {
    new Async1[F[Throwable, _]] {
      override def async[A](effect: (Either[Throwable, A] => Unit) => Unit): F[Throwable, A] = {
        F.uninterruptible(F.async(effect))
      }
      override def fromFuture[A](effect: => Future[A]): F[Throwable, A] = {
        F.fromFuture(effect)
      }
      override def parTraverse_[A](l: IterableOnce[A])(f: A => F[Throwable, Unit]): F[Throwable, Unit] = {
        F.parTraverse_(l.iterator.to(Iterable))(f)
      }
      override def parTraverse[A, B](l: IterableOnce[A])(f: A => F[Throwable, B]): F[Throwable, List[B]] = {
        F.parTraverse(l.iterator.to(Iterable))(f)
      }
      override def parTraverseN[A, B](n: Int)(l: IterableOnce[A])(f: A => F[Throwable, B]): F[Throwable, List[B]] = {
        F.parTraverseN(n)(l.iterator.to(Iterable))(f)
      }
      override def parTraverseN_[A](n: Int)(l: IterableOnce[A])(f: A => F[Throwable, Unit]): F[Throwable, Unit] = {
        F.parTraverseN_(n)(l.iterator.to(Iterable))(f)
      }
    }
  }
}

private[bio] sealed trait LowPriorityAsync1Instances {
  /**
    * This instance uses 'no more orphans' trick to provide an Optional instance
    * only IFF you have cats-effect as a dependency without REQUIRING a cats-effect dependency.
    *
    * Optional instance via https://blog.7mind.io/no-more-orphans.html
    */
  implicit final def fromCats[F[_], Async[_[_]]: `cats.effect.kernel.Async`](implicit F0: Async[F]): Async1[F] = new Async1[F] {
    @inline private def F: cats.effect.kernel.Async[F] = F0.asInstanceOf[cats.effect.kernel.Async[F]]
    private implicit val P: cats.Parallel[F] = cats.effect.kernel.instances.spawn.parallelForGenSpawn(F)

    override def async[A](effect: (Either[Throwable, A] => Unit) => Unit): F[A] = {
      F.uncancelable(_ => F.async_(effect))
    }
    override def fromFuture[A](effect: => Future[A]): F[A] = {
      F.fromFutureCancelable(F.delay(effect -> F.unit))
    }
    override def parTraverse_[A](l: IterableOnce[A])(f: A => F[Unit]): F[Unit] = {
      cats.Parallel.parTraverse_(l.iterator.toList)(f)(using cats.instances.list.catsStdInstancesForList, P)
    }
    override def parTraverse[A, B](l: IterableOnce[A])(f: A => F[B]): F[List[B]] = {
      cats.Parallel.parTraverse(l.iterator.toList)(f)(using cats.instances.list.catsStdInstancesForList, P)
    }
    override def parTraverseN[A, B](n: Int)(l: IterableOnce[A])(f: A => F[B]): F[List[B]] = {
      F.parTraverseN(n)(l.iterator.toList)(f)(using cats.instances.list.catsStdInstancesForList)
    }
    override def parTraverseN_[A](n: Int)(l: IterableOnce[A])(f: A => F[Unit]): F[Unit] = {
      F.void(parTraverseN(n)(l)(f))
    }
  }
}

/**
  * @note Dev note: This was split from Async1 to stop distage-framework-docker runtime from depending on Temporal2 & Clock2,
  *       so that they wouldn't get memoized and the user could override them in tests without destroying memoization.
  */
trait Temporal1[F[_]] {
  def sleep(duration: FiniteDuration): F[Unit]
}

object Temporal1 extends LowPriorityTemporal1Instances {
  def apply[F[_]: Temporal1]: Temporal1[F] = implicitly

  implicit lazy val temporal1Identity: Temporal1[Identity] = new Temporal1[Identity] {
    override def sleep(duration: FiniteDuration): Unit = {
      Thread.sleep(duration.toMillis)
    }
  }

  implicit def fromBIO[F[+_, +_]](implicit F: WeakTemporal2[F]): Temporal1[F[Throwable, _]] = new Temporal1[F[Throwable, _]] {
    override def sleep(duration: FiniteDuration): F[Throwable, Unit] = {
      F.sleep(duration)
    }
  }
}

private[bio] sealed trait LowPriorityTemporal1Instances {
  /**
    * This instance uses 'no more orphans' trick to provide an Optional instance
    * only IFF you have cats-effect as a dependency without REQUIRING a cats-effect dependency.
    *
    * Optional instance via https://blog.7mind.io/no-more-orphans.html
    */
  implicit final def fromCats[F[_], GenTemporal[_[_], _]: `cats.effect.kernel.GenTemporal`](implicit F0: GenTemporal[F, Throwable]): Temporal1[F] =
    new Temporal1[F] {
      override def sleep(duration: FiniteDuration): F[Unit] = {
        F0.asInstanceOf[cats.effect.kernel.GenTemporal[F, Throwable]].sleep(duration)
      }
    }
}
