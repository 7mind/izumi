package izumi.distage.compat

import cats.Parallel
import cats.effect.kernel.Async
import cats.effect.{IO, Resource}
import cats.effect.unsafe.IORuntime
import distage.{DefaultModule, Injector, ModuleDef, Roots}
import izumi.distage.model.Locator
import izumi.functional.quasi.QuasiIORunner
import izumi.fundamentals.platform.functional.Identity
import izumi.distage.testkit.runner.spec.AnyWordSpec
import zio.{Executor, Task, ZIO}

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{ExecutorService, TimeUnit}
import scala.concurrent.duration.DurationInt

final case class NativeEffectResource(value: String)

final class NativeDefaultEffectModuleTest extends AnyWordSpec {
  private val CompletionTimeout = 5.seconds

  "Native default effect modules" should {
    "provision and release a Cats resource through the default module" in {
      val acquired = new AtomicInteger(0)
      val released = new AtomicInteger(0)
      val resource = Resource.make(IO {
        acquired.incrementAndGet()
        NativeEffectResource("provisioned")
      })(_ => IO {
        released.incrementAndGet()
        ()
      })
      val module = new ModuleDef {
        make[NativeEffectResource].fromResource(resource)
      }
      val runtime = IORuntime.builder().build()
      try {
        val effect = Injector[IO]().produce(module, Roots.Everything).use {
          objects => IO {
            assert(objects.get[NativeEffectResource].value == "provisioned")
            ()
          }
        }
        assert(effect.unsafeRunTimed(CompletionTimeout)(runtime).contains(()))
        assert(acquired.get() == 1)
        assert(released.get() == 1)
      } finally runtime.shutdown()
    }

    "provision the ZIO default module" in {
      checkZio(DefaultModule.forZIO, _ => ZIO.succeed(Thread.currentThread()))
    }

    "provision the ZIO and Cats interop default module" in {
      checkZio(DefaultModule.forZIOPlusCats, objects => {
        val async = objects.get[Async[Task]]
        val parallel = objects.get[Parallel[Task]]
        val effect = parallel.sequential(parallel.applicative.map2(parallel.parallel(async.pure(20)), parallel.parallel(async.pure(22)))(_ + _))
        async.map(effect) { value =>
          assert(value == 42)
          Thread.currentThread()
        }
      })
    }
  }

  private def checkZio(module: DefaultModule[Task], body: Locator => Task[Thread]): Unit = {
    val (executor, worker) = Injector[Identity]().produce(module.module, Roots.Everything).use {
      objects =>
        val executor = objects.get[Executor]("cpu").asJava match {
          case service: ExecutorService => service
          case _ => throw new IllegalStateException("Native ZIO compute executor is not an owned service")
        }
        val runner = objects.get[QuasiIORunner[Task]]
        (executor, runner.runBlocking(ZIO.yieldNow *> body(objects)))
    }
    assert(executor.isShutdown)
    assert(executor.awaitTermination(CompletionTimeout.toMillis, TimeUnit.MILLISECONDS))
    worker.join(CompletionTimeout.toMillis)
    val _ = assert(!worker.isAlive)
  }
}
