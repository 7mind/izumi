package izumi.distage.testkit.runner

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success}

private[runner] object CancellationFixtures {
  def run(context: ExecutionContext, verify: (Boolean, String) => Unit): Future[Unit] = {
    implicit val ec: ExecutionContext = context
    val calls = new AtomicInteger(0)
    val gate = Promise[Unit]()
    val requested = new Cancellation
    requested.request()
    val late = requested.onRequest(() => { val _ = calls.incrementAndGet(); gate.future })
    requested.request()
    val firstClose = late.close()
    val secondClose = late.close()
    verify(requested.isRequested && calls.get() == 1, "Late cancellation registration must invoke its action exactly once")
    verify(!firstClose.isCompleted && !secondClose.isCompleted, "Repeated close must await a pending requested action")
    val _ = gate.success(())
    firstClose.flatMap(_ => secondClose).flatMap { _ =>
      val closed = new Cancellation
      val skipped = closed.onRequest(() => { val _ = calls.incrementAndGet(); Future.unit })
      skipped.close().map { _ =>
        closed.request()
        verify(closed.isRequested && calls.get() == 1, "Closing before cancellation must unregister the action")
      }
    }.flatMap { _ =>
      val token = new Cancellation
      val actionEntered = Promise[Unit]()
      val pending = Promise[Unit]()
      val active = token.onRequest(() => { require(actionEntered.trySuccess(())); pending.future })
      val request = Future(token.request())
      actionEntered.future.flatMap { _ =>
        val closing = active.close()
        verify(!closing.isCompleted, "Close after action entry must wait for action completion")
        token.request()
        val _ = pending.success(())
        request.flatMap(_ => closing)
      }
    }.flatMap { _ =>
      val failure = new IllegalStateException("cancellation action failed")
      val failed = new Cancellation
      val throwing = failed.onRequest(() => throw failure)
      val afterFailure = failed.onRequest(() => { val _ = calls.incrementAndGet(); Future.unit })
      val isolated = new Cancellation
      val separate = isolated.onRequest(() => { val _ = calls.incrementAndGet(); Future.unit })
      failed.request()
      failed.request()
      verify(calls.get() == 2 && !isolated.isRequested, "A failed action must not prevent later actions or cancel another owner")
      throwing.close().transformWith {
        case Success(_) => Future.failed(new AssertionError("A failed cancellation action must fail registration close"))
        case Failure(cause) =>
          verify(cause eq failure, "Registration close must retain the original synchronous action failure")
          throwing.close().transformWith {
            case Success(_) => Future.failed(new AssertionError("Repeated close must retain action failure"))
            case Failure(repeated) =>
              verify(repeated eq failure, "Repeated close must retain the same action failure")
              afterFailure.close().flatMap(_ => separate.close()).map { _ =>
                isolated.request()
                verify(calls.get() == 2 && isolated.isRequested, "A closed isolated registration must remain uninvoked")
                println("CANCELLATION_REGISTRATIONS_OK late=true pending=awaited failure=retained owners=isolated")
              }
          }
      }
    }
  }
}
