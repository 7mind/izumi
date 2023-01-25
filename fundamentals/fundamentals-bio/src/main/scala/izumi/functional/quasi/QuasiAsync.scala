package izumi.functional.quasi

import izumi.functional.bio.{Async2, F, Temporal2, UnsafeRun2}
import izumi.fundamentals.orphans.`cats.effect.kernel.Async`
import izumi.fundamentals.platform.functional.Identity

import scala.collection.compat.*
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.concurrent.{Await, ExecutionContext, Future, Promise}

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
  def parTraverseN_[A, B](n: Int)(l: IterableOnce[A])(f: A => F[Unit]): F[Unit]
  def sleep(duration: FiniteDuration): F[Unit]
}

object QuasiAsync extends LowPriorityQuasiAsyncInstances {
  def apply[F[_]: QuasiAsync]: QuasiAsync[F] = implicitly

  implicit lazy val quasiAsyncIdentity: QuasiAsync[Identity] = {
    new QuasiAsync[Identity] {
      final val maxAwaitTime = FiniteDuration(1L, "minute")
      final val QuasiAsyncIdentityPool = izumi.functional.bio.UnsafeRun2.NamedThreadFactory.QuasiAsyncIdentityPool

      override def async[A](effect: (Either[Throwable, A] => Unit) => Unit): Identity[A] = {
        val promise = Promise[A]()
        effect {
          case Right(a) => promise.success(a)
          case Left(f) => promise.failure(f)
        }
        Await.result(promise.future, maxAwaitTime)
      }
      override def parTraverse_[A](l: IterableOnce[A])(f: A => Unit): Unit = {
        parTraverse(l)(f)
        ()
      }
      override def sleep(duration: FiniteDuration): Identity[Unit] = {
        Thread.sleep(duration.toMillis)
      }

      override def parTraverse[A, B](l: IterableOnce[A])(f: A => Identity[B]): Identity[List[B]] = {
        parTraverseIdentity(QuasiAsyncIdentityPool)(l)(f)
      }

      override def parTraverseN[A, B](n: Int)(l: IterableOnce[A])(f: A => Identity[B]): Identity[List[B]] = {
        val limitedAsyncPool = UnsafeRun2.NamedThreadFactory.QuasiAsyncIdentityThreadFactory(n)
        parTraverseIdentity(limitedAsyncPool)(l)(f)
      }

      override def parTraverseN_[A, B](n: Int)(l: IterableOnce[A])(f: A => Identity[Unit]): Identity[Unit] = {
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

  implicit def fromBIO[F[+_, +_]: Async2: Temporal2]: QuasiAsync[F[Throwable, _]] = {
    import scala.collection.compat.*
    new QuasiAsync[F[Throwable, _]] {
      override def async[A](effect: (Either[Throwable, A] => Unit) => Unit): F[Throwable, A] = {
        F.async(effect)
      }
      override def parTraverse_[A](l: IterableOnce[A])(f: A => F[Throwable, Unit]): F[Throwable, Unit] = {
        F.parTraverse_(l.iterator.to(Iterable))(f)
      }
      override def sleep(duration: FiniteDuration): F[Throwable, Unit] = {
        F.sleep(duration)
      }
      override def parTraverse[A, B](l: IterableOnce[A])(f: A => F[Throwable, B]): F[Throwable, List[B]] = {
        F.parTraverse(l.iterator.to(Iterable))(f)
      }
      override def parTraverseN[A, B](n: Int)(l: IterableOnce[A])(f: A => F[Throwable, B]): F[Throwable, List[B]] = {
        F.parTraverseN(n)(l.iterator.to(Iterable))(f)
      }
      override def parTraverseN_[A, B](n: Int)(l: IterableOnce[A])(f: A => F[Throwable, Unit]): F[Throwable, Unit] = {
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
    val F: cats.effect.kernel.Async[F] = F0.asInstanceOf[cats.effect.kernel.Async[F]]
    implicit val P: cats.Parallel[F] = cats.effect.kernel.instances.spawn.parallelForGenSpawn(F)

    override def async[A](effect: (Either[Throwable, A] => Unit) => Unit): F[A] = {
      F.async_(effect)
    }
    override def parTraverse_[A](l: IterableOnce[A])(f: A => F[Unit]): F[Unit] = {
      cats.Parallel.parTraverse_(l.iterator.toList)(f)(cats.instances.list.catsStdInstancesForList, P)
    }
    override def sleep(duration: FiniteDuration): F[Unit] = {
      F.sleep(duration)
    }
    override def parTraverse[A, B](l: IterableOnce[A])(f: A => F[B]): F[List[B]] = {
      cats.Parallel.parTraverse(l.iterator.toList)(f)(cats.instances.list.catsStdInstancesForList, P)
    }
    override def parTraverseN[A, B](n: Int)(l: IterableOnce[A])(f: A => F[B]): F[List[B]] = {
      F.parTraverseN(n)(l.iterator.toList)(f)(cats.instances.list.catsStdInstancesForList)
    }
    override def parTraverseN_[A, B](n: Int)(l: IterableOnce[A])(f: A => F[Unit]): F[Unit] = {
      F.void(parTraverseN(n)(l)(f))
    }
  }
}
