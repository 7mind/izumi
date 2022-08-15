package izumi.functional.bio.test

import izumi.functional.bio.{BlockingIO3, BlockingIOInstances}
import zio.ZIO

trait PlatformDependentTestBase {
  implicit val blockingIO3: BlockingIO3[ZIO] = BlockingIOInstances.fromSyncSafe3
}
