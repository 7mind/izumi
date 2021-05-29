package izumi.functional.bio.laws

import cats.effect.Sync
import cats.effect.laws.SyncLaws
import cats.effect.laws.discipline.SyncTests
import izumi.functional.bio.catz.BIOToSync
import izumi.functional.bio.impl.MiniBIO
import izumi.functional.bio.laws.env.MiniBIOEnv

class MiniBIOLawsTest extends CatsLawsTestBase with MiniBIOEnv {
  val syncTests: SyncTests[MiniBIO[Throwable, `?`]] = new SyncTests[MiniBIO[Throwable, `?`]] {
    override val laws = new SyncLaws[MiniBIO[Throwable, `?`]] {
      override val F = Sync[MiniBIO[Throwable, `?`]]
    }
  }

  checkAll("MiniBIO sync", syncTests.sync[Int, Int, Int])
}
