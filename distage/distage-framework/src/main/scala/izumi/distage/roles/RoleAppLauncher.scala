package izumi.distage.roles

import cats.effect.LiftIO
import distage.{DIResourceBase, TagK, TagKK}
import izumi.distage.roles.AppShutdownStrategy.{BIOShutdownStrategy, CatsEffectIOShutdownStrategy, JvmExitHookLatchShutdownStrategy}
import izumi.distage.roles.services.StartupPlanExecutor.PreparedApp
import izumi.functional.bio.BIOAsync
import izumi.fundamentals.platform.cli.model.raw.RawAppArgs
import izumi.fundamentals.platform.cli.model.schema.ParserDef
import izumi.fundamentals.platform.functional.Identity

import scala.concurrent.ExecutionContext

trait RoleAppLauncher[F[_]] {
  def launch(parameters: RawAppArgs): DIResourceBase[Identity, PreparedApp[F]]
}

object RoleAppLauncher {

  object Options extends ParserDef {
    final val logLevelRootParam = arg("log-level-root", "ll", "root log level", "{trace|debug|info|warn|error|critical}")
    final val logFormatParam = arg("log-format", "lf", "log format", "{hocon|json}")
    final val configParam = arg("config", "c", "path to config file", "<path>")
    final val dumpContext = flag("debug-dump-graph", "dump DI graph for debugging")
    final val use = arg("use", "u", "activate a choice on functionality axis", "<axis>:<choice>")
  }

  abstract class LauncherF[F[_]: TagK: LiftIO](executionContext: ExecutionContext = ExecutionContext.global) extends RoleAppLauncherImpl[F] {
    override protected val shutdownStrategy: AppShutdownStrategy[F] = new CatsEffectIOShutdownStrategy(executionContext)
  }

  abstract class LauncherBIO[F[+_, +_]: TagKK: BIOAsync] extends RoleAppLauncherImpl[F[Throwable, *]] {
    override protected val shutdownStrategy: AppShutdownStrategy[F[Throwable, *]] = new BIOShutdownStrategy[F]
  }

  abstract class LauncherIdentity extends RoleAppLauncherImpl[Identity] {
    override protected val shutdownStrategy: AppShutdownStrategy[Identity] = new JvmExitHookLatchShutdownStrategy
  }

}
