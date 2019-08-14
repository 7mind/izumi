package izumi.distage.roles.test

import cats.effect.IO
import izumi.distage.plugins.load.PluginLoader.PluginConfig
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
    RawRoleParams("testrole00", RawEntrypointParams.empty, Vector.empty),
    RawRoleParams("testrole01", RawEntrypointParams.empty, Vector.empty),
    RawRoleParams("testrole02", RawEntrypointParams.empty, Vector.empty),
    RawRoleParams("testtask00", RawEntrypointParams.empty, Vector.empty),
    RawRoleParams("configwriter", RawEntrypointParams.empty, Vector.empty),
    RawRoleParams("help", RawEntrypointParams.empty, Vector.empty),
  )
}


object TestEntrypoint extends RoleAppMain.Silent(TestLauncher)
