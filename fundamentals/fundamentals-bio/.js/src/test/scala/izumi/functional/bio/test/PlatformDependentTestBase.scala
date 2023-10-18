package izumi.functional.bio.test

import izumi.functional.bio.{BlockingIO2, BlockingIOInstances}

trait PlatformDependentTestBase {
  implicit val blockingIO3: BlockingIO2[zio.IO] = BlockingIOInstances.fromSyncSafe2
}
