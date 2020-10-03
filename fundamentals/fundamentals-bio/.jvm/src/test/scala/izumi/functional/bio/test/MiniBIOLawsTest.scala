package izumi.functional.bio.test

import cats.effect.Sync
import cats.effect.laws.SyncLaws
import cats.effect.laws.discipline.SyncTests
import izumi.functional.bio.catz._
import izumi.functional.bio.env.MiniBIOEnv
import izumi.functional.bio.impl.MiniBIO

class MiniBIOLawsTest extends CatsLawsTestBase with MiniBIOEnv {
    val syncTests = new SyncTests[MiniBIO[Throwable, ?]] {
      val laws = new SyncLaws[MiniBIO[Throwable, ?]] {
        val F = Sync[MiniBIO[Throwable, ?]]
      }
    }

  checkAll("MiniBIO sync", syncTests.sync[Int, Int, Int])
}
