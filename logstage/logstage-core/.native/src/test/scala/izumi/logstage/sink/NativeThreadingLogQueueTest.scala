package izumi.logstage.sink

import izumi.logstage.api.{IzLogger, Log, TestSink}
import izumi.logstage.api.logger.LogSink
import izumi.distage.testkit.runner.spec.AnyWordSpec

import java.util.concurrent.{CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}
import scala.concurrent.duration.DurationInt

class NativeThreadingLogQueueTest extends AnyWordSpec {
  private val CompletionTimeoutMillis = 5000L
  private val DrainObservationMillis = 200L

  "Native threading log queue" should {
    "reject automatic shutdown hook access while supporting explicit close" in {
      val queue = new ThreadingLogQueue(1.millis, 10)
      queue.start()
      try {
        val failure = intercept[UnsupportedOperationException](queue.shutdownHook)
        assert(failure.getMessage.contains("Automatic shutdown draining is unavailable on Scala Native"))
      } finally queue.close()
    }

    "wait for an active sink flush before completing close" in {
      val entered = new CountDownLatch(1)
      val release = new CountDownLatch(1)
      val closing = new CountDownLatch(1)
      val closed = new CountDownLatch(1)
      val closeFailure = new AtomicReference[Option[Throwable]](None)
      val delivered = new TestSink()
      val heldSink = new LogSink {
        override def flush(entry: Log.Entry): Unit = {
          entered.countDown()
          if (!release.await(CompletionTimeoutMillis, TimeUnit.MILLISECONDS)) {
            throw new IllegalStateException("Test did not release the active sink flush")
          }
          delivered.flush(entry)
        }
      }
      val queue = new ThreadingLogQueue(1.millis, 10)
      val closer = new Thread(() => {
        closing.countDown()
        try queue.close()
        catch {
          case failure: Throwable => closeFailure.set(Some(failure))
        } finally closed.countDown()
      })
      closer.setDaemon(true)
      queue.start()
      try {
        IzLogger(IzLogger.Level.Trace, heldSink, buffer = queue).info("held flush")
        assert(entered.await(CompletionTimeoutMillis, TimeUnit.MILLISECONDS))
        closer.start()
        assert(closing.await(CompletionTimeoutMillis, TimeUnit.MILLISECONDS))
        assert(!closed.await(DrainObservationMillis, TimeUnit.MILLISECONDS))
        release.countDown()
        assert(closed.await(CompletionTimeoutMillis, TimeUnit.MILLISECONDS))
        assert(closeFailure.get().isEmpty)
        assert(delivered.fetch().size == 1)
      } finally {
        release.countDown()
        queue.close()
        closer.join(CompletionTimeoutMillis)
      }
      assert(!closer.isAlive)
    }

    "flush and synchronize messages appended after close" in {
      val delivered = new TestSink()
      val synchronizations = new AtomicInteger(0)
      val sink = new LogSink {
        override def flush(entry: Log.Entry): Unit = delivered.flush(entry)
        override def sync(): Unit = {
          synchronizations.incrementAndGet()
          ()
        }
      }
      val queue = new ThreadingLogQueue(1.millis, 10)
      queue.start()
      queue.close()
      IzLogger(IzLogger.Level.Trace, sink, buffer = queue).info("after close")
      assert(delivered.fetch().size == 1)
      assert(synchronizations.get() == 1)
    }
  }
}
