package izumi.functional.bio.laws

import cats.effect.kernel.Sync
import cats.effect.laws.{SyncLaws, SyncTests}
import izumi.functional.bio.catz.BIOToSync
import izumi.functional.bio.impl.MiniBIO
import izumi.functional.bio.laws.env.MiniBIOEnv

class MiniBIOLawsTest extends CatsLawsTestBase with MiniBIOEnv {
  val syncTests: SyncTests[MiniBIO[Throwable, _]] = new SyncTests[MiniBIO[Throwable, _]] {
    override val laws = new SyncLaws[MiniBIO[Throwable, _]] {
      override val F = Sync[MiniBIO[Throwable, _]]
    }
  }

//  checkAll("MiniBIO sync", syncTests.sync[Int, Int, Int])
}
