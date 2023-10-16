package izumi.functional.bio.test

import izumi.functional.bio.{BlockingIO2, BlockingIOInstances}
import zio.ZIO

trait PlatformDependentTestBase {
  implicit val blockingIO3: BlockingIO2[ZIO] = BlockingIOInstances.fromSyncSafe3
}
