package izumi.functional.bio.laws

import cats.effect.kernel.Sync
import cats.effect.laws.{SyncLaws, SyncTests}
import izumi.functional.bio.catz.BIOToSync
import izumi.functional.bio.impl.MiniBIOAsync
import izumi.functional.bio.laws.env.MiniBIOAsyncEnv

class MiniBIOAsyncLawsTest extends CatsLawsTestBase with MiniBIOAsyncEnv {
  val syncTests: SyncTests[MiniBIOAsync[Throwable, _]] = new SyncTests[MiniBIOAsync[Throwable, _]] {
    override val laws: SyncLaws[MiniBIOAsync[Throwable, _]] = new SyncLaws[MiniBIOAsync[Throwable, _]] {
      override val F: Sync[MiniBIOAsync[Throwable, _]] = Sync[MiniBIOAsync[Throwable, _]]
    }
  }

  checkAll("MiniBIOAsync sync", syncTests.sync[Int, Int, Int])
}
