package izumi.distage.roles.test

import cats.effect.IO
import izumi.distage.plugins.load.PluginLoader.PluginConfig
import izumi.distage.roles._
import izumi.distage.roles.services.PluginSource
import izumi.fundamentals.platform.language.SourcePackageMaterializer.thisPkg

class TestLauncher extends RoleAppLauncher.LauncherF[IO] {
  override protected def pluginSource: PluginSource = PluginSource(
    PluginConfig(
      debug = false,
      packagesEnabled = Seq(s"$thisPkg.fixtures"),
      packagesDisabled = Seq.empty,
    )
  )
  override protected val shutdownStrategy: AppShutdownStrategy[IO] = new ImmediateExitShutdownStrategy()
}
