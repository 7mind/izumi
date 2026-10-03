package izumi.distage.roles.test

import izumi.distage.roles.launcher.NativeShutdownStrategy
import izumi.distage.roles.RoleAppMainPlatformSpecific
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.language.Quirks.Discarder
import izumi.logstage.api.IzLogger
import org.scalatest.wordspec.AnyWordSpec

import java.util.concurrent.{CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}

final class NativeShutdownStrategyTest extends AnyWordSpec {
  "Native shutdown strategy" should {
    "let its launcher request return while other threads wait for the outer scope" in {
      val strategy = new NativeShutdownStrategy[Identity]
      val requested = new CountDownLatch(1)
      val completed = new CountDownLatch(1)
      val requester = new Thread(() => {
        requested.countDown()
        strategy.releaseAwaitLatch()
        completed.countDown()
      })
      val CompletionTimeoutMillis = 5000L
      val CleanupObservationMillis = 200L
      try {
        RoleAppMainPlatformSpecific.runMain[Identity] {
          observe =>
            observe(Some(strategy))
            strategy.finishShutdown()
            strategy.releaseAwaitLatch()
            requester.start()
            assert(requested.await(CompletionTimeoutMillis, TimeUnit.MILLISECONDS))
            assert(!completed.await(CleanupObservationMillis, TimeUnit.MILLISECONDS)).discard()
        }
        assert(completed.await(CompletionTimeoutMillis, TimeUnit.MILLISECONDS))
      } finally {
        strategy.completeShutdown()
        if (requester.getState != Thread.State.NEW) requester.join(CompletionTimeoutMillis)
      }
      assert(!requester.isAlive)
    }

    "reject a second observation before deferring it and complete the first scope" in {
      val first = new NativeShutdownStrategy[Identity]
      val second = new NativeShutdownStrategy[Identity]
      intercept[IllegalArgumentException] {
        RoleAppMainPlatformSpecific.runMain[Identity] {
          observe =>
            observe(Some(first))
            first.finishShutdown()
            observe(Some(second))
        }
      }
      second.finishShutdown()
      val requester = new Thread(() => {
        first.releaseAwaitLatch()
        second.releaseAwaitLatch()
      })
      val CompletionTimeoutMillis = 5000L
      requester.start()
      try {
        requester.join(CompletionTimeoutMillis)
        assert(!requester.isAlive)
      } finally {
        first.completeShutdown()
        second.completeShutdown()
        requester.join(CompletionTimeoutMillis)
      }
    }

    "let concurrent requests wait for cleanup and reject duplicate awaits" in {
      val strategy = new NativeShutdownStrategy[Identity]
      val cleanup = new AtomicInteger(0)
      val failures = new AtomicReference(Option.empty[Throwable])
      val owner = new Thread(() => {
        try {
          strategy.awaitShutdown(IzLogger.NullLogger)
          cleanup.incrementAndGet().discard()
        } catch {
          case error: Throwable => failures.set(Some(error))
        } finally strategy.finishShutdown()
      }, "native-shutdown-test-owner")
      val requesters = Vector.tabulate(2)(n => new Thread(() => strategy.releaseAwaitLatch(), s"native-shutdown-test-requester-$n"))
      val ReadinessTimeoutNanos = 5000000000L
      val CompletionTimeoutMillis = 5000L
      owner.start()
      try {
        val deadline = System.nanoTime() + ReadinessTimeoutNanos
        while (owner.getState != Thread.State.WAITING && System.nanoTime() < deadline) Thread.sleep(1L)
        assert(owner.getState == Thread.State.WAITING)
        intercept[IllegalStateException](strategy.awaitShutdown(IzLogger.NullLogger))
        requesters.foreach(_.start())
        owner.join(CompletionTimeoutMillis)
        requesters.foreach(_.join(CompletionTimeoutMillis))
        assert(!owner.isAlive && requesters.forall(!_.isAlive))
        assert(failures.get().isEmpty)
        assert(cleanup.get() == 1)
      } finally {
        strategy.finishShutdown()
        strategy.releaseAwaitLatch()
        owner.join(CompletionTimeoutMillis)
        requesters.filter(_.getState != Thread.State.NEW).foreach(_.join(CompletionTimeoutMillis))
      }
    }
  }
}
