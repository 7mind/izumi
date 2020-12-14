package izumi.distage.roles.test

import cats.effect.IO
import distage.plugins.PluginConfig
import izumi.distage.model.definition.{Module, ModuleDef}
import izumi.distage.roles.RoleAppMain
import izumi.distage.roles.launcher.AppFailureHandler
import izumi.distage.roles.launcher.AppShutdownStrategy.ImmediateExitShutdownStrategy
import izumi.distage.roles.test.fixtures.Fixture.XXX_LocatorLeak
import izumi.fundamentals.platform.language.SourcePackageMaterializer.thisPkg

object TestEntrypoint extends TestEntrypointBase

// for `CompTimePlanCheckerTest`
object TestEntrypointPatchedLeak extends TestEntrypointBase {
  override protected def roleAppBootOverrides(argv: RoleAppMain.ArgV): Module = super.roleAppBootOverrides(argv) ++ new ModuleDef {
    modify[Module].named("roleapp") {
      _ ++ new ModuleDef {
        todo[XXX_LocatorLeak]
      }
    }
  }
}

class TestEntrypointBase extends RoleAppMain.LauncherCats[IO] {
  override protected def pluginConfig: PluginConfig = {
    PluginConfig.cached(Seq(s"$thisPkg.fixtures"))
  }

  override protected def shutdownStrategy: ImmediateExitShutdownStrategy[IO] = {
    new ImmediateExitShutdownStrategy()
  }

  override protected def earlyFailureHandler(args: RoleAppMain.ArgV): AppFailureHandler = {
    AppFailureHandler.NullHandler
  }
}
