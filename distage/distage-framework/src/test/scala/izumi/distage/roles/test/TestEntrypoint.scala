package izumi.distage.roles.test

import cats.effect.IO
import distage.plugins.PluginConfig
import izumi.distage.model.definition.{Module, ModuleDef}
import izumi.distage.roles.RoleAppMain
import izumi.distage.roles.launcher.AppFailureHandler
import izumi.distage.roles.launcher.AppShutdownStrategy.ImmediateExitShutdownStrategy
import izumi.distage.roles.test.fixtures.Fixture.XXX_LocatorLeak
import izumi.fundamentals.platform.IzPlatform
import izumi.fundamentals.platform.language.SourcePackageMaterializer

object TestEntrypoint extends TestEntrypointBase

// for `CompTimePlanCheckerTest`
object TestEntrypointPatchedLeak extends TestEntrypointPatchedLeakBase

trait TestEntrypointPatchedLeakBase extends TestEntrypointBase {
  override protected def roleAppBootOverrides(argv: RoleAppMain.ArgV): Module = super.roleAppBootOverrides(argv) ++ new ModuleDef {
    modify[Module].named("roleapp") {
      _ ++ new ModuleDef {
        todo[XXX_LocatorLeak]
      }
    }
  }
}

class TestEntrypointBase extends RoleAppMain.Launcher1[IO] {
  override protected def pluginConfig: PluginConfig = {
    if (IzPlatform.isScalaJS) {
      __ScalaJSFixturesPlugins.pluginConfig
    } else {
      PluginConfig.cached(Seq(s"${SourcePackageMaterializer.thisPkg}.fixtures"))
    }
  }

  override protected def shutdownStrategy: ImmediateExitShutdownStrategy[IO] = {
    new ImmediateExitShutdownStrategy()
  }

  override protected def earlyFailureHandler(args: RoleAppMain.ArgV): AppFailureHandler = {
    AppFailureHandler.NullHandler
  }
}

object ManualTestEntrypoint extends RoleAppMain.Launcher1[IO] {
  override protected def pluginConfig: PluginConfig = {
    if (IzPlatform.isScalaJS) {
      __ScalaJSFixturesPlugins.pluginConfig
    } else {
      PluginConfig.cached(Seq(s"${SourcePackageMaterializer.thisPkg}.fixtures"))
    }
  }
}

object __ScalaJSFixturesPlugins {
  def pluginConfig: PluginConfig = {
    PluginConfig.const(
      Seq(
//        new fixtures.TestPluginCatsIO, // contain references to ExecutorService
//        new fixtures.ResourcesPlugin, // contain references to ExecutorService
        new fixtures.ConflictPlugin,
        new fixtures.AdaptedAutocloseablesCasePlugin,
      )
    )
  }
}
