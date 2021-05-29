package izumi.distage.roles.test

import distage.plugins.PluginConfig
import izumi.distage.roles.RoleAppMain
import izumi.distage.roles.launcher.AppFailureHandler
import izumi.distage.roles.test.fixtures.{ExitAfterSleepRole, TestPluginBase}
import izumi.fundamentals.platform.cli.model.raw.RawRoleParams

class TestPluginZIO extends TestPluginBase[zio.IO[Throwable, _]]

class ManualTestEntrypointBase extends RoleAppMain.LauncherBIO2[zio.IO] {

  /** Roles always enabled in this [[RoleAppMain]] */
  override protected def requiredRoles(argv: RoleAppMain.ArgV): Vector[RawRoleParams] = Vector(RawRoleParams(ExitAfterSleepRole.id))

  override protected def pluginConfig: PluginConfig = {
    PluginConfig.const(new TestPluginZIO())
  }

  override protected def earlyFailureHandler(args: RoleAppMain.ArgV): AppFailureHandler = {
    AppFailureHandler.PrintingHandler
  }
}

object ManualTestEntrypoint extends ManualTestEntrypointBase
