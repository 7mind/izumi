package izumi.distage.testkit.runner.di

import distage.{ModuleDef, TagK}
import izumi.distage.testkit.runner.impl.services.TestkitLogging
import izumi.functional.lifecycle.Lifecycle
import izumi.functional.quasi.{QuasiAsync, QuasiIO, QuasiIORunner}
import izumi.fundamentals.platform.functional.Identity

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.Promise

private[di] class OwnedRuntimeFixture(val graphFailure: Throwable, val outerFailure: Throwable, fails: Boolean) extends ResourceStatistics {
  val outerAcquired = new AtomicInteger(0)
  val outerReleased = new AtomicInteger(0)
  val graphAcquired = new AtomicInteger(0)
  val graphReleased = new AtomicInteger(0)
  val releasing = Promise[Unit]()
  val release = Promise[Unit]()

  def runtime[F[_]: TagK](delegate: Lifecycle[Identity, QuasiIORunner[F]])(implicit F: QuasiIO[F], FA: QuasiAsync[F]): TestRunnerRuntime = {
    val lifecycle = Lifecycle.makeSimple[Unit] { val _ = outerAcquired.incrementAndGet(); () } { _ =>
      val _ = outerReleased.incrementAndGet()
      if (fails) throw outerFailure
    }.flatMap(_ => delegate)
    val overrides = new ModuleDef {
      make[TestkitLogging].fromResource { () => Lifecycle.make[F, TestkitLogging](F.maybeSuspend {
        val _ = graphAcquired.incrementAndGet()
        new TestkitLogging { override def enableDebugOutput: Boolean = false }
      }) { _ =>
        F.flatMap(F.maybeSuspend { val _ = releasing.success(()) })(_ =>
          F.flatMap(FA.fromFuture(release.future))(_ => F.flatMap(F.maybeSuspend { val _ = graphReleased.incrementAndGet() })(_ =>
            if (fails) F.fail[Unit](graphFailure) else F.unit
          ))
        )
      } }
    }
    TestRunnerRuntime.asyncRuntimeFor[F](lifecycle, List(overrides))
  }
}
