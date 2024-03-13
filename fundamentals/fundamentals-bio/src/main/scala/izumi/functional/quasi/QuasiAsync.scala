package izumi.functional.quasi

import izumi.functional.bio.{Async2, F, Temporal2}
import izumi.fundamentals.orphans.{`cats.effect.kernel.Async`, `cats.effect.kernel.GenTemporal`}
import izumi.fundamentals.platform.functional.Identity

import scala.collection.compat.*
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.concurrent.*

/**
  * Parallel & async operations for `F` required by `distage-*` libraries.
  * Unlike `QuasiIO` there's nothing "quasi" about it – it makes sense. But named like that for consistency anyway.
  *
  * Internal use class, as with [[QuasiIO]], it's only public so that you can define your own instances,
  * better use [[izumi.functional.bio]] or [[cats]] typeclasses for application logic.
  *
  * TODO: we want to get rid of this by providing Identity implementations for Parallel3, Async3 and Temporal3
  * See https://github.com/7mind/izumi/issues/787
  */
trait QuasiAsync[F[_]] {
  def async[A](effect: (Either[Throwable, A] => Unit) => Unit): F[A]
  def parTraverse[A, B](l: IterableOnce[A])(f: A => F[B]): F[List[B]]
  def parTraverse_[A](l: IterableOnce[A])(f: A => F[Unit]): F[Unit]
  def parTraverseN[A, B](n: Int)(l: IterableOnce[A])(f: A => F[B]): F[List[B]]
  def parTraverseN_[A](n: Int)(l: IterableOnce[A])(f: A => F[Unit]): F[Unit]
}

object QuasiAsync extends LowPriorityQuasiAsyncInstances {
  def apply[F[_]: QuasiAsync]: QuasiAsync[F] = implicitly

  implicit lazy val quasiAsyncIdentity: QuasiAsync[Identity] = {
    new QuasiAsync[Identity] {
      override def async[A](effect: (Either[Throwable, A] => Unit) => Unit): Identity[A] = {
        val promise = Promise[A]()
        effect {
          case Right(a) => promise.success(a)
          case Left(f) => promise.failure(f)
        }
        Await.result(promise.future, FiniteDuration(1L, "minute"))
      }

      override def parTraverse_[A](l: IterableOnce[A])(f: A => Unit): Unit = {
        parTraverse(l)(f)
        ()
      }

      override def parTraverse[A, B](l: IterableOnce[A])(f: A => Identity[B]): Identity[List[B]] = {
        parTraverseIdentity(__QuasiAsyncPlatformSpecific.QuasiAsyncIdentityBlockingIOPool)(l)(f)
      }

      override def parTraverseN[A, B](n: Int)(l: IterableOnce[A])(f: A => Identity[B]): Identity[List[B]] = {
        __QuasiAsyncPlatformSpecific
          .QuasiAsyncIdentityCreateLimitedThreadPool(n)
          .use {
            limitedAsyncEC =>
              parTraverseIdentity(limitedAsyncEC)(l)(f)
          }
      }

      override def parTraverseN_[A](n: Int)(l: IterableOnce[A])(f: A => Identity[Unit]): Identity[Unit] = {
        parTraverseN(n)(l)(f)
        ()
      }
    }
  }

  private[izumi] def parTraverseIdentity[A, B](ec0: ExecutionContext)(l: IterableOnce[A])(f: A => Identity[B]): Identity[List[B]] = {
    implicit val ec: ExecutionContext = ec0
    val future = Future.sequence(l.iterator.map(a => Future(scala.concurrent.blocking(f(a)))))
    Await.result(future, Duration.Inf).toList
  }

  implicit def fromBIO[F[+_, +_]: Async2]: QuasiAsync[F[Throwable, _]] = {
    new QuasiAsync[F[Throwable, _]] {
      override def async[A](effect: (Either[Throwable, A] => Unit) => Unit): F[Throwable, A] = {
        F.async(effect)
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

private[quasi] sealed trait LowPriorityQuasiAsyncInstances {
  /**
    * This instance uses 'no more orphans' trick to provide an Optional instance
    * only IFF you have cats-effect as a dependency without REQUIRING a cats-effect dependency.
    *
    * Optional instance via https://blog.7mind.io/no-more-orphans.html
    */
  implicit final def fromCats[F[_], Async[_[_]]: `cats.effect.kernel.Async`](implicit F0: Async[F]): QuasiAsync[F] = new QuasiAsync[F] {
    @inline private def F: cats.effect.kernel.Async[F] = F0.asInstanceOf[cats.effect.kernel.Async[F]]
    private implicit val P: cats.Parallel[F] = cats.effect.kernel.instances.spawn.parallelForGenSpawn(F)

    override def async[A](effect: (Either[Throwable, A] => Unit) => Unit): F[A] = {
      F.async_(effect)
    }
    override def parTraverse_[A](l: IterableOnce[A])(f: A => F[Unit]): F[Unit] = {
      cats.Parallel.parTraverse_(l.iterator.toList)(f)(cats.instances.list.catsStdInstancesForList, P)
    }
    override def parTraverse[A, B](l: IterableOnce[A])(f: A => F[B]): F[List[B]] = {
      cats.Parallel.parTraverse(l.iterator.toList)(f)(cats.instances.list.catsStdInstancesForList, P)
    }
    override def parTraverseN[A, B](n: Int)(l: IterableOnce[A])(f: A => F[B]): F[List[B]] = {
      F.parTraverseN(n)(l.iterator.toList)(f)(cats.instances.list.catsStdInstancesForList)
    }
    override def parTraverseN_[A](n: Int)(l: IterableOnce[A])(f: A => F[Unit]): F[Unit] = {
      F.void(parTraverseN(n)(l)(f))
    }
  }
}

/**
  * @note Dev note: This was split from QuasiAsync to stop distage-testkit runtime from depending on Temporal2 & Clock2,
  *       so that they wouldn't get memoized and the user could override them in tests without destroying memoization.
  */
trait QuasiTemporal[F[_]] {
  def sleep(duration: FiniteDuration): F[Unit]
}

object QuasiTemporal extends LowPriorityQuasiTimerInstances {
  def apply[F[_]: QuasiTemporal]: QuasiTemporal[F] = implicitly

  implicit lazy val quasiTimerIdentity: QuasiTemporal[Identity] = new QuasiTemporal[Identity] {
    override def sleep(duration: FiniteDuration): Identity[Unit] = {
      Thread.sleep(duration.toMillis)
    }
  }

  implicit def fromBIO[F[+_, +_]](implicit F: Temporal2[F]): QuasiTemporal[F[Throwable, _]] = new QuasiTemporal[F[Throwable, _]] {
    override def sleep(duration: FiniteDuration): F[Throwable, Unit] = {
      F.sleep(duration)
    }
  }
}

private[quasi] sealed trait LowPriorityQuasiTimerInstances {
  /**
    * This instance uses 'no more orphans' trick to provide an Optional instance
    * only IFF you have cats-effect as a dependency without REQUIRING a cats-effect dependency.
    *
    * Optional instance via https://blog.7mind.io/no-more-orphans.html
    */
  implicit final def fromCats[F[_], GenTemporal[_[_], _]: `cats.effect.kernel.GenTemporal`](implicit F0: GenTemporal[F, Throwable]): QuasiTemporal[F] =
    new QuasiTemporal[F] {
      override def sleep(duration: FiniteDuration): F[Unit] = {
        F0.asInstanceOf[cats.effect.kernel.GenTemporal[F, Throwable]].sleep(duration)
      }
    }
}
