package izumi.functional.bio.impl

import izumi.functional.bio.UnsafeRun2

import scala.annotation.unused
import scala.concurrent.ExecutionContext

trait MiniBIOPlatformSpecific {
  protected abstract class MiniBIOUnsafeRun2UnsafeRunSyncPlatformSpecific(implicit @unused ec: ExecutionContext) extends UnsafeRun2[MiniBIO]
}
