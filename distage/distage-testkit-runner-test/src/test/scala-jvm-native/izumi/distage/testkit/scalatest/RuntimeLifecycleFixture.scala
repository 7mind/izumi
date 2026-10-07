package izumi.distage.testkit.scalatest

import izumi.functional.bio.impl.MiniBIOAsync
import izumi.functional.lifecycle.Lifecycle
import izumi.functional.quasi.QuasiIORunner
import izumi.fundamentals.platform.functional.Identity
import scala.concurrent.ExecutionContext
import java.util.concurrent.Executors

private[scalatest] object RuntimeLifecycleFixture {
  def miniBIO(): Lifecycle[Identity, QuasiIORunner[MiniBIOAsync[Throwable, _]]] = {
    val context: Lifecycle[Identity, ExecutionContext] = Lifecycle.fromExecutorService(Executors.newCachedThreadPool()).map(service => ExecutionContext.fromExecutorService(service))
    context.map(ec => QuasiIORunner.fromBIO[MiniBIOAsync](using MiniBIOAsync.UnsafeRunMiniBIOAsync(using ec)))
  }
}
