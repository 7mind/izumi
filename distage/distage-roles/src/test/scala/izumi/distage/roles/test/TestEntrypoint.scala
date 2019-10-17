package izumi.distage.roles.test

import cats.effect.IO
import izumi.distage.plugins.load.PluginLoader.PluginConfig
import izumi.distage.roles.internal.{ConfigWriter, Help}
import izumi.distage.roles.test.fixtures.{AdoptedAutocloseablesCase, TestRole00, TestRole01, TestRole02, TestTask00}
import izumi.distage.roles.{AppShutdownStrategy, BootstrapConfig, ImmediateExitShutdownStrategy, RoleAppLauncher, RoleAppMain}
import izumi.fundamentals.platform.cli.model.raw.{RawEntrypointParams, RawRoleParams}
import izumi.fundamentals.reflection.SourcePackageMaterializer.thisPkg

class TestLauncherBase extends RoleAppLauncher.LauncherF[IO]() {
  protected val bootstrapConfig: BootstrapConfig = BootstrapConfig(
    PluginConfig(
      debug = false
      , packagesEnabled = Seq(s"$thisPkg.fixtures")
      , packagesDisabled = Seq.empty
    )
  )
}

object ExampleLauncher extends TestLauncherBase

object TestLauncher extends TestLauncherBase {
  override protected val hook: AppShutdownStrategy[IO] = new ImmediateExitShutdownStrategy()
}


object ExampleEntrypoint extends RoleAppMain.Default(TestLauncher) {
  override protected def requiredRoles: Vector[RawRoleParams] = Vector(
    RawRoleParams(AdoptedAutocloseablesCase.id, RawEntrypointParams.empty, Vector.empty),
    RawRoleParams(TestRole00.id, RawEntrypointParams.empty, Vector.empty),
    RawRoleParams(TestRole01.id, RawEntrypointParams.empty, Vector.empty),
    RawRoleParams(TestRole02.id, RawEntrypointParams.empty, Vector.empty),
    RawRoleParams(TestTask00.id, RawEntrypointParams.empty, Vector.empty),
    RawRoleParams(ConfigWriter.id, RawEntrypointParams.empty, Vector.empty),
    RawRoleParams(Help.id, RawEntrypointParams.empty, Vector.empty),
  )
}


object TestEntrypoint extends RoleAppMain.Silent(TestLauncher)
