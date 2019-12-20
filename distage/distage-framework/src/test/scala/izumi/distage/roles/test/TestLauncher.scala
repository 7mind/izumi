package izumi.distage.roles.test

import cats.effect.IO
import izumi.distage.framework.model.PluginSource
import izumi.distage.plugins.PluginConfig
import izumi.distage.roles.AppShutdownStrategy.ImmediateExitShutdownStrategy
import izumi.distage.roles._
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
