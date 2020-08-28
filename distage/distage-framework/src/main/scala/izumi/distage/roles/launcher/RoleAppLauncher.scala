package izumi.distage.roles.launcher

import distage.DIResourceBase
import izumi.distage.roles.launcher.services.StartupPlanExecutor.PreparedApp
import izumi.fundamentals.platform.cli.model.raw.RawAppArgs
import izumi.fundamentals.platform.functional.Identity

trait RoleAppLauncher[F[_]] {
  def launch(parameters: RawAppArgs): DIResourceBase[Identity, PreparedApp[F]]
}

//object RoleAppLauncher {
//  abstract class LauncherF[F[_]: TagK: LiftIO](executionContext: ExecutionContext = ExecutionContext.global) extends RoleAppLauncherImpl[F] {
//    override protected val shutdownStrategy: AppShutdownStrategy[F] = new CatsEffectIOShutdownStrategy(executionContext)
//  }
//
//  abstract class LauncherBIO[F[+_, +_]: TagKK: BIOAsync] extends RoleAppLauncherImpl[F[Throwable, *]] {
//    override protected val shutdownStrategy: AppShutdownStrategy[F[Throwable, *]] = new BIOShutdownStrategy[F]
//  }
//
//  abstract class LauncherIdentity extends RoleAppLauncherImpl[Identity] {
//    override protected val shutdownStrategy: AppShutdownStrategy[Identity] = new JvmExitHookLatchShutdownStrategy
//  }
//}
