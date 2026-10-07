package izumi.distage.testkit.runner.di

import izumi.functional.lifecycle.Lifecycle
import izumi.functional.quasi.QuasiIORunner
import izumi.fundamentals.platform.functional.Identity

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Try}

final case class RuntimeExecution[A](completion: Future[A], stop: () => Future[Unit])

final class TestRuntime[F[_]](
  lifecycle: Lifecycle[Identity, QuasiIORunner[F]],
  completionContext: ExecutionContext,
) {
  def run[A](effect: => F[A]): RuntimeExecution[A] = start(_.runFutureInterruptible(effect))

  private[di] def start[A](launch: QuasiIORunner[F] => (Future[A], () => Future[Unit])): RuntimeExecution[A] = {
    val allocation = lifecycle.acquire

    def release(result: Try[A]): Try[A] = {
      val failures = try { lifecycle.release(allocation); Nil } catch { case cause: Throwable => List(cause) }
      combineFailures(result, failures)
    }

    val (execution, interrupt) = try launch(lifecycle.extract(allocation).merge)
    catch { case cause: Throwable => throw release(Failure(cause)).failed.get }

    val lock = new Object
    var completed = false
    var stopping = Option.empty[Promise[Unit]]

    def stop(): Future[Unit] = {
      val (result, admitted) = lock.synchronized {
        stopping match {
          case Some(previous) => (previous.future, None)
          case None if completed => (Future.unit, None)
          case None =>
            val requested = Promise[Unit]()
            stopping = Some(requested)
            (requested.future, Some(requested))
        }
      }
      admitted.foreach { requested =>
        try { val _ = requested.completeWith(interrupt()) }
        catch { case cause: Throwable => val _ = requested.failure(cause) }
      }
      result
    }

    val finalized = execution.transformWith { result =>
      val pending = lock.synchronized {
        completed = true
        stopping.map(_.future)
      }
      pending match {
        case Some(requested) => requested.transform(stopped => release(combineFailures(result, stopped.failed.toOption.toList)))(completionContext)
        case None => Future.fromTry(release(result))
      }
    }(completionContext)
    RuntimeExecution(finalized, () => stop())
  }

  private def combineFailures[A](result: Try[A], additional: List[Throwable]): Try[A] = {
    (result.failed.toOption.toList ++ additional) match {
      case Nil => result
      case cause :: Nil => Failure(cause)
      case primary :: others => Failure(new RuntimeCompletionException(primary, others))
    }
  }

  private final class RuntimeCompletionException(primary: Throwable, additional: List[Throwable])
    extends RuntimeException("Multiple failures during test runtime completion", primary) {
    additional.foreach(addSuppressed)
  }
}
