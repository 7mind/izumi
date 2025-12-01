package izumi.functional.quasi

import izumi.functional.bio.Exit
import izumi.functional.bio.UnsafeRun2.NamedThreadFactory
import izumi.functional.bio.impl.MiniBIOAsync
import izumi.fundamentals.platform.functional.Identity

import java.util.concurrent.Executors
import scala.collection.compat.*
import scala.concurrent.*
import scala.concurrent.duration.Duration

private[quasi] object __QuasiAsyncPlatformSpecific {
  private val factory = new NamedThreadFactory("QuasiIO-cached-pool", daemon = true, priority = None)

  private final lazy val QuasiAsyncIdentityBlockingIOPool = ExecutionContext.fromExecutorService {
    Executors.newCachedThreadPool(factory)
  }

  def quasiAsyncIdentity: QuasiAsync[Identity] = {
    new QuasiAsync[Identity] {
      override def async[A](effect: (Either[Throwable, A] => Unit) => Unit): Identity[A] = {
        val promise = Promise[A]()
        effect {
          case Right(a) => promise.success(a)
          case Left(f) => promise.failure(f)
        }
        Await.result(promise.future, Duration.Inf)
      }

      override def fromFuture[A](effect: => Future[A]): Identity[A] = {
        Await.result(effect, Duration.Inf)
      }

      override def parTraverse_[A](l: IterableOnce[A])(f: A => Unit): Unit = {
        parTraverseIdentityImpl(l, f)(MiniBIOAsync.WeakAsync2ForMiniBIOAsync.parTraverse_)(QuasiAsyncIdentityBlockingIOPool)
      }

      override def parTraverse[A, B](l: IterableOnce[A])(f: A => Identity[B]): Identity[List[B]] = {
        parTraverseIdentityImpl(l, f)(MiniBIOAsync.WeakAsync2ForMiniBIOAsync.parTraverse)(QuasiAsyncIdentityBlockingIOPool)
      }

      override def parTraverseN[A, B](n: Int)(l: IterableOnce[A])(f: A => Identity[B]): Identity[List[B]] = {
        parTraverseIdentityImpl(l, f)(MiniBIOAsync.WeakAsync2ForMiniBIOAsync.parTraverseN(n))(QuasiAsyncIdentityBlockingIOPool)
      }

      override def parTraverseN_[A](n: Int)(l: IterableOnce[A])(f: A => Identity[Unit]): Identity[Unit] = {
        parTraverseIdentityImpl(l, f)(MiniBIOAsync.WeakAsync2ForMiniBIOAsync.parTraverseN_(n))(QuasiAsyncIdentityBlockingIOPool)
      }
    }
  }

  private def parTraverseIdentityImpl[A, B, C](
    l: IterableOnce[A],
    f: A => Identity[B],
  )(parTraverseImpl: Iterable[A] => (A => MiniBIOAsync[Throwable, B]) => MiniBIOAsync[Throwable, C]
  )(ec: ExecutionContext
  ): Identity[C] = {
    val F = MiniBIOAsync.WeakAsync2ForMiniBIOAsync
    val future = parTraverseImpl(l.iterator.to(Iterable))(a => F.syncBlocking(f(a))).runSyncToFirstAsyncBoundaryOrOnEC(ec)
    Await.result(future, Duration.Inf) match {
      case Exit.Success(value) => value
      case failure: Exit.FailureUninterrupted[Throwable] => throw failure.toThrowable
    }
  }

}
