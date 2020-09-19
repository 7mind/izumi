package izumi.distage.roles.test

import cats.effect.IO
import distage.plugins.PluginConfig
import izumi.distage.roles.RoleAppMain
import izumi.distage.roles.launcher.AppShutdownStrategy.ImmediateExitShutdownStrategy
import izumi.fundamentals.platform.language.SourcePackageMaterializer.thisPkg

object TestEntrypoint extends TestEntrypointBase

class TestEntrypointBase extends RoleAppMain.LauncherF[IO] {
  override protected def pluginConfig: PluginConfig = {
    PluginConfig.cached(Seq(s"$thisPkg.fixtures"))
  }

  override protected def shutdownStrategy: ImmediateExitShutdownStrategy[IO] = {
    new ImmediateExitShutdownStrategy()
  }
}
