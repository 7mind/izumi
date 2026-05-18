package izumi.distage.testkit.distagesuite.generic

import izumi.distage.plugins.PluginConfig
import izumi.distage.testkit.distagesuite.fixtures.MockUserRepository
import izumi.distage.testkit.model.TestConfig
import izumi.distage.testkit.scalatest.{Spec2, SpecIdentity}
import izumi.functional.bio.Bifunctorized
import zio.ZIO

// JVM-only tests that exercise `Thread.sleep` semantics through the testkit.
//
// ZIO variant uses `ZIO.attempt(Thread.sleep(...))` directly.
// Identity variant uses plain `Thread.sleep` — the SpecIdentity DSL lifts the body
// through `Bifunctorized.bifunctorizeIdentity` (=> `MiniBIO.syncThrowable`), so Thread.sleep
// blocks the calling thread synchronously. This restores the pre-bifunctorization
// `Identity` sleep coverage that was dropped during M5/11c when the typeclass mediating
// this path (`QuasiTemporal[Identity]`) was removed; the equivalent capability is now
// `Temporal2[IdentityBifunctorized]` (M5-fix5a). The bifunctor `CIO` variant is dropped
// because it would only duplicate the `Spec2[Bifunctorized[CIO, +_, +_]]` `syncThrowable`
// path without adding distinct coverage.
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

abstract class DistageSleepTestIdentity extends SpecIdentity {
  override protected def config: TestConfig = {
    super.config.copy(
      pluginConfig = PluginConfig.cached(packagesEnabled = Seq("izumi.distage.testkit.distagesuite.fixtures"))
    )
  }

  "distage test" should {
    "sleep" in {
      (_: MockUserRepository[Bifunctorized.IdentityBifunctorized]) =>
        Thread.sleep(100)
    }
  }
}

final class IdentityDistageSleepTest01 extends DistageSleepTestIdentity
final class IdentityDistageSleepTest02 extends DistageSleepTestIdentity
final class IdentityDistageSleepTest03 extends DistageSleepTestIdentity
