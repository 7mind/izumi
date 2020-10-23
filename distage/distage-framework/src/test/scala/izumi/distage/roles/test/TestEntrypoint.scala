package izumi.distage.roles.test

import cats.effect.IO
import distage.plugins.PluginConfig
import izumi.distage.model.definition
import izumi.distage.model.definition.ModuleDef
import izumi.distage.roles.RoleAppMain
import izumi.distage.roles.launcher.AppFailureHandler
import izumi.distage.roles.launcher.AppShutdownStrategy.ImmediateExitShutdownStrategy
import izumi.distage.roles.test.fixtures.Fixture.XXX_LocatorLeak
import izumi.fundamentals.platform.language.SourcePackageMaterializer.thisPkg

object TestEntrypoint extends TestEntrypointBase

// for `CompTimePlanCheckerTest`
object TestEntrypointPatchedLeak extends TestEntrypointBase {
  override protected def appModuleOverrides(argv: RoleAppMain.ArgV): definition.Module = super.appModuleOverrides(argv) ++ new ModuleDef {
    todo[XXX_LocatorLeak]
  }
}

class TestEntrypointBase extends RoleAppMain.LauncherCats[IO] {
  override protected def pluginConfig: PluginConfig = {
    PluginConfig.cached(Seq(s"$thisPkg.fixtures"))
  }

  override protected def shutdownStrategy: ImmediateExitShutdownStrategy[IO] = {
    new ImmediateExitShutdownStrategy()
  }

  override protected def createEarlyFailureHandler(args: RoleAppMain.ArgV): AppFailureHandler = {
    AppFailureHandler.NullHandler
  }
}