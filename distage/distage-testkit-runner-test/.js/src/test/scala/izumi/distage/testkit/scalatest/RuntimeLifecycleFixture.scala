package izumi.distage.testkit.scalatest

import izumi.functional.bio.impl.MiniBIOAsync
import izumi.functional.lifecycle.Lifecycle
import izumi.functional.quasi.QuasiIORunner
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.IzPlatform
import scala.concurrent.ExecutionContext

private[scalatest] object RuntimeLifecycleFixture {
  def miniBIO(): Lifecycle[Identity, QuasiIORunner[MiniBIOAsync[Throwable, _]]] = {
    val context: Lifecycle[Identity, ExecutionContext] = Lifecycle.pure(IzPlatform.platformGlobalExecutionContext)
    context.map(ec => QuasiIORunner.fromBIO[MiniBIOAsync](using MiniBIOAsync.UnsafeRunMiniBIOAsync(using ec)))
  }
}
