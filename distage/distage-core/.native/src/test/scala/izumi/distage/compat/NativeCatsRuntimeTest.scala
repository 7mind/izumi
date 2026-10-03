package izumi.distage.compat

import cats.effect.{FileDescriptorPoller, IO}
import cats.effect.unsafe.IORuntime
import distage.{Injector, ModuleDef, Roots}
import izumi.distage.modules.support.CatsIOSupportModule
import izumi.fundamentals.platform.functional.Identity
import org.scalatest.wordspec.AnyWordSpec

import java.util.concurrent.{CountDownLatch, Executors, TimeUnit}
import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.*
import scala.scalanative.meta.LinktimeInfo

final class NativeCatsRuntimeTest extends AnyWordSpec {
  private val CompletionTimeout = 5.seconds

  "Native Cats runtime module" should {
    "install the Native poller and release its compute and blocking workers" in {
      val workers = Injector[Identity]().produce(CatsIOSupportModule, Roots.Everything).use {
        objects =>
          val runtime = objects.get[IORuntime]
          assert(runtime.compute eq objects.get[ExecutionContext]("cpu"))
          assert(run(IO.pollers, runtime).nonEmpty)
          if (LinktimeInfo.isLinux || LinktimeInfo.isMac) {
            assert(run(FileDescriptorPoller.find, runtime).nonEmpty)
          }
          assert(run(IO.sleep(1.millis).as("scheduled"), runtime) == "scheduled")
          Vector(run(IO.delay(Thread.currentThread()), runtime), executedThread(objects.get[ExecutionContext]("io")))
      }
      workers.distinct.foreach {
        worker =>
          worker.join(CompletionTimeout.toMillis)
          assert(!worker.isAlive)
      }
    }

    "honor named executor overrides without taking their ownership" in {
      val computeService = Executors.newSingleThreadExecutor()
      val blockingService = Executors.newSingleThreadExecutor()
      val compute = ExecutionContext.fromExecutor(computeService)
      val blocking = ExecutionContext.fromExecutor(blockingService)
      try {
        val overrides = new ModuleDef {
          make[ExecutionContext].named("cpu").fromValue(compute)
          make[ExecutionContext].named("io").fromValue(blocking)
        }
        Injector[Identity]().produce(CatsIOSupportModule.overriddenBy(overrides), Roots.Everything).use {
          objects =>
            val runtime = objects.get[IORuntime]
            assert(runtime.compute eq compute)
            assert(objects.get[ExecutionContext]("io") eq blocking)
            assert(run(IO.delay(Thread.currentThread()), runtime) eq executedThread(compute))
            assert(run(IO.blocking(Thread.currentThread()), runtime) eq executedThread(blocking))
        }
        assert(!computeService.isShutdown)
        assert(!blockingService.isShutdown)
      } finally {
        computeService.shutdown()
        blockingService.shutdown()
        assert(computeService.awaitTermination(CompletionTimeout.toMillis, TimeUnit.MILLISECONDS))
        val _ = assert(blockingService.awaitTermination(CompletionTimeout.toMillis, TimeUnit.MILLISECONDS))
      }
    }
  }

  private def run[A](effect: IO[A], runtime: IORuntime): A = {
    effect.unsafeRunTimed(CompletionTimeout)(runtime).getOrElse {
      throw new IllegalStateException("Native Cats effect did not complete within the test timeout")
    }
  }

  private def executedThread(context: ExecutionContext): Thread = {
    val thread = new AtomicReference[Option[Thread]](None)
    val completed = new CountDownLatch(1)
    context.execute(() => {
      thread.set(Some(Thread.currentThread()))
      completed.countDown()
    })
    assert(completed.await(CompletionTimeout.toMillis, TimeUnit.MILLISECONDS))
    thread.get().getOrElse {
      throw new IllegalStateException("Execution context did not record its worker thread")
    }
  }
}
