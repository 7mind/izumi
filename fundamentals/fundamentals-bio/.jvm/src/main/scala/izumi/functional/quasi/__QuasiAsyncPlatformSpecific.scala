package izumi.functional.quasi

import izumi.functional.bio.UnsafeRun2.NamedThreadFactory
import izumi.functional.lifecycle.Lifecycle
import izumi.fundamentals.platform.functional.Identity

import java.util.concurrent.Executors
import scala.concurrent.ExecutionContext

import scala.collection.compat.*
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.concurrent.*

private[izumi] object __QuasiAsyncPlatformSpecific {
  private val factory = new NamedThreadFactory("QuasiIO-cached-pool", daemon = true)

  private final lazy val QuasiAsyncIdentityBlockingIOPool = ExecutionContext.fromExecutorService {
    Executors.newCachedThreadPool(factory)
  }

  private[izumi] def quasiAsyncIdentity: QuasiAsync[Identity] = {
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
        parTraverseIdentity(QuasiAsyncIdentityBlockingIOPool)(l)(f)
      }

      override def parTraverseN[A, B](n: Int)(l: IterableOnce[A])(f: A => Identity[B]): Identity[List[B]] = {
        QuasiAsyncIdentityCreateLimitedThreadPool(n)
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

  private final def QuasiAsyncIdentityCreateLimitedThreadPool(max: Int): Lifecycle[Identity, ExecutionContext] = {
    Lifecycle
      .fromExecutorService {
        Executors.newFixedThreadPool(max, factory)
      }.map {
        ExecutionContext.fromExecutorService
      }
  }

  private def parTraverseIdentity[A, B](ec0: ExecutionContext)(l: IterableOnce[A])(f: A => Identity[B]): Identity[List[B]] = {
    implicit val ec: ExecutionContext = ec0
    val future = Future.sequence(l.iterator.map(a => Future(scala.concurrent.blocking(f(a)))))
    Await.result(future, Duration.Inf).toList
  }

}
