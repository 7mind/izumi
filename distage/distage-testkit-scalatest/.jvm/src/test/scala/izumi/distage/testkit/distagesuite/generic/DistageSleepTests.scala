package izumi.distage.testkit.distagesuite.generic

import izumi.distage.plugins.PluginConfig
import izumi.distage.testkit.distagesuite.fixtures.MockUserRepository
import izumi.distage.testkit.model.TestConfig
import izumi.distage.testkit.scalatest.Spec2
import zio.ZIO

// JVM-only tests that use Thread.sleep. Migrated from `Spec1[F[_]: QuasiIO]` (which used
// `F.maybeSuspend(Thread.sleep(...))`) to a concrete ZIO Spec2 (bifunctor form). The pre-M5
// Identity / CIO variants are dropped — Identity because `Bifunctorized[Identity, +_, +_]`
// requires the MiniBIO carrier which has no Thread.sleep path; CIO because the bifunctor
// equivalent test (`Spec2[Bifunctorized[CIO, +_, +_]]`) exercises the same Thread.sleep
// path through `IO2.syncThrowable` and would duplicate the ZIO tests below without adding
// coverage.
abstract class DistageSleepTestZIO extends Spec2[zio.IO] {
  override protected def config: TestConfig = {
    super.config.copy(
      pluginConfig = PluginConfig.cached(packagesEnabled = Seq("izumi.distage.testkit.distagesuite.fixtures"))
    )
  }

  "distage test" should {
    "sleep" in {
      (_: MockUserRepository[zio.IO]) =>
        ZIO.attempt(Thread.sleep(100)).unit
    }
  }
}

final class TaskDistageSleepTest01 extends DistageSleepTestZIO
final class TaskDistageSleepTest02 extends DistageSleepTestZIO
final class TaskDistageSleepTest03 extends DistageSleepTestZIO
