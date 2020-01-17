package izumi.distage.roles.test

import cats.effect.IO
import izumi.distage.plugins.load.PluginLoader
import izumi.distage.roles.AppShutdownStrategy.ImmediateExitShutdownStrategy
import izumi.distage.roles._
import izumi.fundamentals.platform.language.SourcePackageMaterializer.thisPkg

class TestLauncher extends RoleAppLauncher.LauncherF[IO] {
  override protected def pluginLoader: PluginLoader =
    PluginLoader.cached(Seq(s"$thisPkg.fixtures"))
  override protected val shutdownStrategy: AppShutdownStrategy[IO] = new ImmediateExitShutdownStrategy()
}
