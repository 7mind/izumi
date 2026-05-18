package izumi.distage.testkit.distagesuite.integration

import distage.{TagKK, *}
import izumi.distage.model.definition.{Lifecycle, ModuleDef}
import izumi.distage.model.provisioning.IntegrationCheck
import izumi.distage.modules.DefaultModule2
import izumi.distage.testkit.model.TestConfig
import izumi.distage.testkit.scalatest.Spec2
import izumi.functional.bio.{Applicative2, IO2}
import izumi.fundamentals.platform.integration.ResourceCheck

case class TestEnableDisable()

class DisabledTestF2[F[+_, +_]: Applicative2] extends Lifecycle.Basic[F, Nothing, TestEnableDisable] with IntegrationCheck[F[Throwable, _]] {
  override def resourcesAvailable(): F[Throwable, ResourceCheck] =
    Applicative2[F].pure(ResourceCheck.ResourceUnavailable("This test is intentionally disabled.", None))
  override def acquire: F[Nothing, TestEnableDisable] = Applicative2[F].pure(TestEnableDisable())
  override def release(resource: TestEnableDisable): F[Nothing, Unit] = Applicative2[F].unit
}

/** Bifunctor version of the original `MyDisabledTestF2` — uses `IntegrationCheck[F[Throwable, _]]`
  * (Session 5 migration shape).
  */
abstract class MyDisabledTestF2[F[+_, +_]: DefaultModule2: TagKK](implicit F: IO2[F]) extends Spec2[F] {
  override def config: TestConfig = {
    super.config.copy(
      moduleOverrides = super.config.moduleOverrides ++ new ModuleDef {
        make[TestEnableDisable].fromResource[F, Nothing, DisabledTestF2[F]]
      }
    )
  }

  "My component" should {
    "this test should be skipped" in {
      (_: TestEnableDisable) =>
        F.fail(new Throwable("Test was not skipped!")).asInstanceOf[F[Throwable, Unit]]
    }
  }
}

// `IntegrationCheck[F[Throwable, _]]` is now matched by the runner — see M5-fix5b which mirrors
// the EffectStrategy/ResourceStrategy Identity special-case into
// `PlanInterpreterNonSequentialRuntimeImpl.runIfIntegrationCheck` (the binding extends
// `IntegrationCheck[F[Throwable, _]]` and the runner's `checkOrFailF[F]` invokes
// `resourcesAvailable()` and routes the resulting `F[Throwable, ResourceCheck]` through the
// surrounding sandbox).
final class MyDisabledTestF2ZioIO extends MyDisabledTestF2[zio.IO]
