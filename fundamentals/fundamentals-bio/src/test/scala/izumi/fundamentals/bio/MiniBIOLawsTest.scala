package izumi.fundamentals.bio

import cats.effect.Sync
import cats.effect.laws.SyncLaws
import cats.effect.laws.discipline.SyncTests
import izumi.functional.bio.catz._
import izumi.functional.bio.impl.MiniBIO
import izumi.fundamentals.bio.env.MiniBIOEnv

class MiniBIOLawsTest extends CatsLawsTestBase with MiniBIOEnv {
    val syncTests = new SyncTests[MiniBIO[Throwable, ?]] {
      val laws = new SyncLaws[MiniBIO[Throwable, ?]] {
        val F = Sync[MiniBIO[Throwable, ?]]
      }
    }

  checkAll("MiniBIO sync", syncTests.sync[Int, Int, Int])
}
