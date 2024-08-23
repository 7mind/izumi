package izumi.distage.roles.test

import distage.plugins.PluginConfig
import izumi.distage.roles.RoleAppMain
import izumi.distage.roles.launcher.AppFailureHandler
import izumi.distage.roles.test.fixtures.{ExitAfterSleepRole, TestPluginBase}
import izumi.fundamentals.platform.cli.model.raw.RawRoleParams

class TestPluginZIO extends TestPluginBase[zio.IO[Throwable, _]]

class ExitLatchEntrypointBase extends RoleAppMain.LauncherBIO[zio.IO] {

  /** Roles always enabled in this [[RoleAppMain]] */
  override protected def requiredRoles(argv: RoleAppMain.ArgV): Vector[RawRoleParams] = Vector(RawRoleParams(ExitAfterSleepRole.id))

  override protected def pluginConfig: PluginConfig = {
    PluginConfig.const(new TestPluginZIO())
  }

  override protected def earlyFailureHandler(args: RoleAppMain.ArgV): AppFailureHandler = {
    AppFailureHandler.NullHandler
  }
}

object ExitLatchTestEntrypoint extends ExitLatchEntrypointBase
