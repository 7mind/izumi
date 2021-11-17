package izumi.distage.model.effect

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{Executors, ThreadFactory}
import cats.Parallel
import cats.effect.{Concurrent, Timer}
import izumi.functional.bio.{Async2, F, Temporal2}
import izumi.fundamentals.orphans.{`cats.Parallel`, `cats.effect.Concurrent`, `cats.effect.Timer`}
import izumi.fundamentals.platform.functional.Identity

import scala.annotation.nowarn
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.concurrent.{Await, ExecutionContext, Future, Promise}
import scala.collection.compat.*

/**
  * Parallel & async operations for `F` required by `distage-*` libraries.
  * Unlike `QuasiIO` there's nothing "quasi" about it – it makes sense. But named like that for consistency anyway.
  *
  * Internal use class, as with [[QuasiIO]], it's only public so that you can define your own instances,
  * better use [[izumi.functional.bio]] or [[cats]] typeclasses for application logic.
  */
trait QuasiAsync[F[_]] {
  def async[A](effect: (Either[Throwable, A] => Unit) => Unit): F[A]
  def parTraverse_[A](l: IterableOnce[A])(f: A => F[Unit]): F[Unit]
  def parTraverse[A, B](l: IterableOnce[A])(f: A => F[B]): F[List[B]]
  def parTraverseN[A, B](n: Int)(l: IterableOnce[A])(f: A => F[B]): F[List[B]]
  def parTraverseN_[A, B](n: Int)(l: IterableOnce[A])(f: A => F[Unit]): F[Unit]
  def sleep(duration: FiniteDuration): F[Unit]
}

object QuasiAsync extends LowPriorityQuasiAsyncInstances {
  def apply[F[_]: QuasiAsync]: QuasiAsync[F] = implicitly

  implicit lazy val quasiAsyncIdentity: QuasiAsync[Identity] = {
    new QuasiAsync[Identity] {
      final val maxAwaitTime = FiniteDuration(1L, "minute")
      final val QuasiAsyncIdentityThreadFactory = new NamedThreadFactory("QuasiIO-cached-pool", daemon = true)
      final val QuasiAsyncIdentityPool = ExecutionContext.fromExecutorService {
        Executors.newCachedThreadPool(QuasiAsyncIdentityThreadFactory)
      }

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
        val limitedAsyncPool = ExecutionContext.fromExecutorService {
          Executors.newFixedThreadPool(n, QuasiAsyncIdentityThreadFactory)
        }
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
    import scala.collection.compat._
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

  private[distage] final class NamedThreadFactory(name: String, daemon: Boolean) extends ThreadFactory {

    @nowarn("msg=getSecurityManager")
    private val parentGroup =
      Option(System.getSecurityManager).fold(Thread.currentThread().getThreadGroup)(_.getThreadGroup)

    private val threadGroup = new ThreadGroup(parentGroup, name)
    private val threadCount = new AtomicInteger(1)
    private val threadHash = Integer.toUnsignedString(this.hashCode())

    override def newThread(r: Runnable): Thread = {
      val newThreadNumber = threadCount.getAndIncrement()

      val thread = new Thread(threadGroup, r)
      thread.setName(s"$name-$newThreadNumber-$threadHash")
      thread.setDaemon(daemon)

      thread
    }

  }

}

private[effect] sealed trait LowPriorityQuasiAsyncInstances {
  /**
    * This instance uses 'no more orphans' trick to provide an Optional instance
    * only IFF you have cats-effect as a dependency without REQUIRING a cats-effect dependency.
    *
    * Optional instance via https://blog.7mind.io/no-more-orphans.html
    */
  implicit final def fromCats[F[_], P[_[_]]: `cats.Parallel`, T[_[_]]: `cats.effect.Timer`, C[_[_]]: `cats.effect.Concurrent`](
    implicit
    P: P[F],
    T: T[F],
    C: C[F],
  ): QuasiAsync[F] = {
    new QuasiAsync[F] {
      override def async[A](effect: (Either[Throwable, A] => Unit) => Unit): F[A] = {
        C.asInstanceOf[Concurrent[F]].async(effect)
      }
      override def parTraverse_[A](l: IterableOnce[A])(f: A => F[Unit]): F[Unit] = {
        Parallel.parTraverse_(l.iterator.toList)(f)(cats.instances.list.catsStdInstancesForList, P.asInstanceOf[Parallel[F]])
      }
      override def sleep(duration: FiniteDuration): F[Unit] = {
        T.asInstanceOf[Timer[F]].sleep(duration)
      }
      override def parTraverse[A, B](l: IterableOnce[A])(f: A => F[B]): F[List[B]] = {
        Parallel.parTraverse(l.iterator.toList)(f)(cats.instances.list.catsStdInstancesForList, P.asInstanceOf[Parallel[F]])
      }
      override def parTraverseN[A, B](n: Int)(l: IterableOnce[A])(f: A => F[B]): F[List[B]] = {
        Concurrent.parTraverseN(n.toLong)(l.iterator.toList)(f)(cats.instances.list.catsStdInstancesForList, C.asInstanceOf[Concurrent[F]], P.asInstanceOf[Parallel[F]])
      }
      override def parTraverseN_[A, B](n: Int)(l: IterableOnce[A])(f: A => F[Unit]): F[Unit] = {
        C.asInstanceOf[Concurrent[F]].void(parTraverseN(n)(l)(f))
      }
    }
  }
}
