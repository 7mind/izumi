package izumi.distage.roles.launcher

import distage.DIResourceBase
import izumi.distage.roles.launcher.services.StartupPlanExecutor.PreparedApp
import izumi.fundamentals.platform.cli.model.raw.RawAppArgs
import izumi.fundamentals.platform.functional.Identity

trait RoleAppLauncher[F[_]] {
  def launch(parameters: RawAppArgs): DIResourceBase[Identity, PreparedApp[F]]
}
