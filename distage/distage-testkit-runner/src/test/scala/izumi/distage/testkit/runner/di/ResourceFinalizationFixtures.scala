package izumi.distage.testkit.runner.di

import distage.{Injector, ModuleDef, PlannerInput, Roots}
import izumi.distage.testkit.runner.api.TestFinalizationReporter
import izumi.distage.testkit.runner.impl.services.TestResourceLifecycle
import izumi.functional.bio.{Exit, UnsafeRun2}
import izumi.functional.lifecycle.Lifecycle
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.language.Quirks.Discarder
import zio.ZIO

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.{ExecutionContext, Future}

private[di] object ResourceFinalizationFixtures {
  private final class Resource
  private final class ReleaseFailure(suppression: Boolean) extends RuntimeException("original DI release failure", null, suppression, true)

  def run(context: ExecutionContext, verify: (String, Boolean) => Unit): Future[Unit] = {
    implicit val ec: ExecutionContext = context
    val runtime = UnsafeRun2.createZIO[Any](Some(zio.Executor.fromExecutionContext(context)))
    Vector(true, false).foldLeft(Future.successful(())) { (before, suppression) => before.flatMap { _ =>
      val original = new ReleaseFailure(suppression)
      val acquired = new AtomicInteger(0)
      val released = new AtomicInteger(0)
      val observer = new RecordingReporter
      val producer = Injector[Identity]()
      val module = new ModuleDef {
        make[Resource].fromResource {
          () => Lifecycle.make[zio.Task, Resource](ZIO.succeed { acquired.incrementAndGet().discard(); new Resource }) { _ =>
            ZIO.succeed(released.incrementAndGet().discard()).flatMap(_ => ZIO.fail(original))
          }
        }
      }
      val plan = producer.plan(PlannerInput(module, Roots.target[Resource])).fold(errors => throw new IllegalStateException(errors.toString), identity)
      val lifecycle = new TestResourceLifecycle[zio.Task](observer).produce(producer, plan)
      runtime.unsafeRunAsyncAsFuture(lifecycle.use(_ => ZIO.unit).exit).map { exit =>
        val recorded = observer.failures
        val returned = exit match { case Exit.Success(zio.Exit.Failure(cause)) => cause.failures.toVector ++ cause.defects; case _ => Vector.empty }
        val propagated = returned.size == 1 && (returned.head eq original)
        println("DISTAGE_FINALIZATION_ORIGINAL_CAUSE suppression=" + suppression + " observed=" + recorded.map(_.getClass.getName) + " returned=" + returned.map(_.getClass.getName) + " observerOriginal=" + (recorded.size == 1 && (recorded.head eq original)) + " propagatedOriginal=" + propagated)
        verify("ZIO DI finalizer with suppression=" + suppression + " executes and observes release exactly once", acquired.get() == 1 && released.get() == 1 && recorded.size == 1)
        verify("ZIO DI finalizer with suppression=" + suppression + " observes the original throwable", recorded.head eq original)
        verify("ZIO DI finalizer with suppression=" + suppression + " propagates the original throwable", propagated)
      }
    } }
  }

  private final class RecordingReporter extends TestFinalizationReporter {
    private var recorded = Vector.empty[Throwable]
    def failures: Vector[Throwable] = synchronized(recorded)
    override def failure(cause: Throwable): Unit = synchronized { recorded :+= cause }
  }
}
