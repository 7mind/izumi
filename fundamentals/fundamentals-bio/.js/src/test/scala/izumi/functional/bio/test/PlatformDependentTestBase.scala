package izumi.functional.bio.test

import izumi.functional.bio.BlockingIO3
import zio.ZIO

trait PlatformDependentTestBase {
  implicit val blockingIO2: BlockingIO3[ZIO] = BlockingIO3.fromSyncSafe3
}
