package izumi.fundamentals.platform.concurrent

import izumi.fundamentals.platform.IzPlatformFunctionCollection

import scala.concurrent.duration.Duration
import scala.concurrent.{CanAwait, ExecutionContext, Future}
import scala.util.{Success, Try}

import scala.language.implicitConversions

trait IzFuture extends IzPlatformFunctionCollection {
  implicit final def toRichFuture[A](future: Future[A]): IzFuture.IzFuture_Syntax[A] = new IzFuture.IzFuture_Syntax[A](future)
}

object IzFuture extends IzFuture {

  final class IzFuture_Syntax[A](private val future: Future[A]) extends AnyVal {
    /** @param transform0 must be total and trivial */
    def transformedFuture[B](transform0: Try[A] => Try[B]): Future[B] = {
      new Future[B] {
        override def onComplete[U](f: Try[B] => U)(implicit executor: ExecutionContext): Unit = {
          future.onComplete(t => f(transform0(t)))
        }
        override def isCompleted: Boolean = {
          future.isCompleted
        }
        override def value: Option[Try[B]] = {
          future.value.map(transform0)
        }
        override def transform[S](f: Try[B] => Try[S])(implicit executor: ExecutionContext): Future[S] = {
          future.transform(t => f(transform0(t)))
        }
        override def transformWith[S](f: Try[B] => Future[S])(implicit executor: ExecutionContext): Future[S] = {
          future.transformWith(t => f(transform0(t)))
        }
        override def ready(atMost: Duration)(implicit permit: CanAwait): this.type = {
          future.ready(atMost)
          this
        }
        override def result(atMost: Duration)(implicit permit: CanAwait): B = {
          transform0(Success(future.result(atMost))).get
        }
      }
    }
  }

}
