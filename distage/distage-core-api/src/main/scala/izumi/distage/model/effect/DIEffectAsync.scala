package izumi.distage.model.effect

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{Executors, ThreadFactory}

import cats.Parallel
import cats.effect.{Concurrent, Timer}
import izumi.distage.model.effect.LowPriorityDIEffectAsyncInstances.{_Concurrent, _Parallel, _Timer}
import izumi.functional.bio.{BIOAsync, BIOTemporal, F}
import izumi.fundamentals.platform.functional.Identity

import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.concurrent.{Await, ExecutionContext, Future, Promise}

trait DIEffectAsync[F[_]] {
  def async[A](effect: (Either[Throwable, A] => Unit) => Unit): F[A]
  def parTraverse_[A](l: Iterable[A])(f: A => F[Unit]): F[Unit]
  def parTraverse[A, B](l: Iterable[A])(f: A => F[B]): F[List[B]]
  def parTraverseN[A, B](n: Int)(l: Iterable[A])(f: A => F[B]): F[List[B]]
  def parTraverseN_[A, B](n: Int)(l: Iterable[A])(f: A => F[Unit]): F[Unit]
  def sleep(duration: FiniteDuration): F[Unit]
}

object DIEffectAsync extends LowPriorityDIEffectAsyncInstances {
  def apply[F[_]: DIEffectAsync]: DIEffectAsync[F] = implicitly

  implicit val diEffectParIdentity: DIEffectAsync[Identity] = {
    new DIEffectAsync[Identity] {
      final val maxAwaitTime = FiniteDuration(1L, "minute")
      final val DIEffectAsyncIdentityThreadFactory = new NamedThreadFactory("dieffect-cached-pool", daemon = true)
      final val DIEffectAsyncIdentityPool = ExecutionContext.fromExecutorService {
        Executors.newCachedThreadPool(DIEffectAsyncIdentityThreadFactory)
      }

      override def async[A](effect: (Either[Throwable, A] => Unit) => Unit): Identity[A] = {
        val promise = Promise[A]()
        effect {
          case Right(a) => promise.success(a)
          case Left(f) => promise.failure(f)
        }
        Await.result(promise.future, maxAwaitTime)
      }
      override def parTraverse_[A](l: Iterable[A])(f: A => Unit): Unit = {
        parTraverse(l)(f)
        ()
      }
      override def sleep(duration: FiniteDuration): Identity[Unit] = {
        Thread.sleep(duration.toMillis)
      }

      override def parTraverse[A, B](l: Iterable[A])(f: A => Identity[B]): Identity[List[B]] = {
        parTraverseIdentity(DIEffectAsyncIdentityPool)(l)(f)
      }

      override def parTraverseN[A, B](n: Int)(l: Iterable[A])(f: A => Identity[B]): Identity[List[B]] = {
        val limitedAsyncPool = ExecutionContext.fromExecutorService {
          Executors.newFixedThreadPool(n, DIEffectAsyncIdentityThreadFactory)
        }
        parTraverseIdentity(limitedAsyncPool)(l)(f)
      }

      override def parTraverseN_[A, B](n: Int)(l: Iterable[A])(f: A => Identity[Unit]): Identity[Unit] = {
        parTraverseN(n)(l)(f)
        ()
      }
    }
  }

  implicit def fromBIOTemporal[F[+_, +_]: BIOAsync: BIOTemporal]: DIEffectAsync[F[Throwable, ?]] = {
    new DIEffectAsync[F[Throwable, ?]] {
      override def async[A](effect: (Either[Throwable, A] => Unit) => Unit): F[Throwable, A] = {
        F.async(effect)
      }
      override def parTraverse_[A](l: Iterable[A])(f: A => F[Throwable, Unit]): F[Throwable, Unit] = {
        F.parTraverse_(l)(f)
      }
      override def sleep(duration: FiniteDuration): F[Throwable, Unit] = {
        F.sleep(duration)
      }
      override def parTraverse[A, B](l: Iterable[A])(f: A => F[Throwable, B]): F[Throwable, List[B]] = {
        F.parTraverse(l)(f)
      }
      override def parTraverseN[A, B](n: Int)(l: Iterable[A])(f: A => F[Throwable, B]): F[Throwable, List[B]] = {
        F.parTraverseN(n)(l)(f)
      }
      override def parTraverseN_[A, B](n: Int)(l: Iterable[A])(f: A => F[Throwable, Unit]): F[Throwable, Unit] = {
        F.parTraverseN_(n)(l)(f)
      }
    }
  }

  private[izumi] def parTraverseIdentity[A, B](ec0: ExecutionContext)(l: Iterable[A])(f: A => Identity[B]): Identity[List[B]] = {
    implicit val ec: ExecutionContext = ec0
    val future = Future.sequence(l.map(a => Future(scala.concurrent.blocking(f(a)))))
    Await.result(future, Duration.Inf).toList
  }

  private[distage] final class NamedThreadFactory(name: String, daemon: Boolean) extends ThreadFactory {

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

private[effect] sealed trait LowPriorityDIEffectAsyncInstances {
  /**
    * This instance uses 'no more orphans' trick to provide an Optional instance
    * only IFF you have cats-effect as a dependency without REQUIRING a cats-effect dependency.
    *
    * Optional instance via https://blog.7mind.io/no-more-orphans.html
    */
  implicit final def fromCats[F[_], P[_[_]]: _Parallel, T[_[_]]: _Timer, C[_[_]]: _Concurrent](implicit P: P[F], T: T[F], C: C[F]): DIEffectAsync[F] = {
    new DIEffectAsync[F] {
      override def async[A](effect: (Either[Throwable, A] => Unit) => Unit): F[A] = {
        C.asInstanceOf[Concurrent[F]].async(effect)
      }
      override def parTraverse_[A](l: Iterable[A])(f: A => F[Unit]): F[Unit] = {
        Parallel.parTraverse_(l.toList)(f)(cats.instances.list.catsStdInstancesForList, P.asInstanceOf[Parallel[F]])
      }
      override def sleep(duration: FiniteDuration): F[Unit] = {
        T.asInstanceOf[Timer[F]].sleep(duration)
      }
      override def parTraverse[A, B](l: Iterable[A])(f: A => F[B]): F[List[B]] = {
        Parallel.parTraverse(l.toList)(f)(cats.instances.list.catsStdInstancesForList, P.asInstanceOf[Parallel[F]])
      }
      override def parTraverseN[A, B](n: Int)(l: Iterable[A])(f: A => F[B]): F[List[B]] = {
        Concurrent.parTraverseN(n.toLong)(l.toList)(f)(cats.instances.list.catsStdInstancesForList, C.asInstanceOf[Concurrent[F]], P.asInstanceOf[Parallel[F]])
      }
      override def parTraverseN_[A, B](n: Int)(l: Iterable[A])(f: A => F[Unit]): F[Unit] = {
        C.asInstanceOf[Concurrent[F]].void(parTraverseN(n)(l)(f))
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

  sealed trait _Concurrent[K[_[_]]]
  object _Concurrent {
    @inline implicit final def get: _Concurrent[cats.effect.Concurrent] = null
  }
}
