package izumi.distage.testkit.distagesuite.integration

import cats.Applicative
import distage.{TagK, TagKK}
import izumi.distage.model.provisioning.IntegrationCheck
import izumi.distage.model.definition.{Lifecycle, ModuleDef}
import izumi.distage.model.effect.QuasiIO
import izumi.distage.modules.{DefaultModule, DefaultModule2}
import izumi.distage.testkit.TestConfig
import izumi.distage.testkit.scalatest.{Spec1, Spec2}
import izumi.functional.bio.catz.*
import izumi.functional.bio.{Applicative2, ApplicativeError2, F}
import izumi.fundamentals.platform.integration.ResourceCheck
import zio.{Task, UIO}

case class TestEnableDisable()

class DisabledTestZIO extends Lifecycle.Simple[TestEnableDisable] with IntegrationCheck[UIO] {
  override def resourcesAvailable(): UIO[ResourceCheck] =
    UIO.succeed(ResourceCheck.ResourceUnavailable("This test is intentionally disabled.", None))

  override def acquire: TestEnableDisable = TestEnableDisable()
  override def release(resource: TestEnableDisable): Unit = ()
}

class MyDisabledTestZIO extends Spec1[Task] {
  override def config: TestConfig = super.config.copy(
    moduleOverrides = new ModuleDef {
      make[TestEnableDisable].fromResource[DisabledTestZIO]
    }
  )

  "My component" should {
    "this test should be skipped" in { _: TestEnableDisable => Task.fail(new Throwable("Test was not skipped!")).unit }
  }
}

class DisabledTestF[F[_]](implicit F: Applicative[F]) extends Lifecycle.Basic[F, TestEnableDisable] with IntegrationCheck[F] {
  override def resourcesAvailable(): F[ResourceCheck] =
    F.pure(ResourceCheck.ResourceUnavailable("This test is intentionally disabled.", None))

  override def acquire: F[TestEnableDisable] = F.pure(TestEnableDisable())
  override def release(resource: TestEnableDisable): F[Unit] = F.unit
}

abstract class MyDisabledTestF[F0[_]: QuasiIO: TagK: DefaultModule, F[x] <: F0[x]: TagK](implicit F: Applicative[F]) extends Spec1[F0] {
  override def config: TestConfig = {
    super.config.copy(
      moduleOverrides = new ModuleDef {
        make[TestEnableDisable].fromResource[DisabledTestF[F]]
        addImplicit[Applicative[F]]
      }
    )
  }

  "My component" should {
    "this test should be skipped" in { _: TestEnableDisable => F.map[Unit, Unit](F.unit)(_ => throw new Throwable("Test was not skipped!")) }
  }
}

final class MyDisabledTestFCats extends MyDisabledTestF[cats.effect.IO, cats.effect.IO]
//final class MyDisabledTestFMonixTask extends MyDisabledTestF[monix.eval.Task, monix.eval.Task]
//final class MyDisabledTestFMonixBIOUIO extends MyDisabledTestF[monix.bio.Task, monix.bio.UIO]
//final class MyDisabledTestFMonixBIOTask extends MyDisabledTestF[monix.bio.Task, monix.bio.Task]
final class MyDisabledTestFZioUIO extends MyDisabledTestF[zio.Task, zio.UIO]
final class MyDisabledTestFZioTask extends MyDisabledTestF[zio.Task, zio.Task]

class DisabledTestF2[F[+_, +_]: Applicative2] extends Lifecycle.Basic[F[Nothing, +_], TestEnableDisable] with IntegrationCheck[F[Nothing, _]] {
  override def resourcesAvailable(): F[Nothing, ResourceCheck] =
    F.pure(ResourceCheck.ResourceUnavailable("This test is intentionally disabled.", None))
  override def acquire: F[Nothing, TestEnableDisable] = F.pure(TestEnableDisable())
  override def release(resource: TestEnableDisable): F[Nothing, Unit] = F.unit
}

abstract class MyDisabledTestF2[F[+_, +_]: DefaultModule2: TagKK](implicit F: ApplicativeError2[F]) extends Spec2[F] {
  override def config: TestConfig = {
    super.config.copy(
      moduleOverrides = new ModuleDef {
        make[TestEnableDisable].fromResource[DisabledTestF2[F]]
      }
    )
  }

  "My component" should {
    "this test should be skipped" in { _: TestEnableDisable => F.fail(new Throwable("Test was not skipped!")).void }
  }
}

//final class MyDisabledTestF2MonixBIO extends MyDisabledTestF2[monix.bio.IO]
final class MyDisabledTestF2ZioIO extends MyDisabledTestF2[zio.IO]
//final class MyDisabledTestF2ZIOZIOZEnv extends MyDisabledTestF2[zio.ZIO[zio.ZEnv, +_, +_]]
