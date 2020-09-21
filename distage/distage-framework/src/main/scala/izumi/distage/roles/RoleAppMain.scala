package izumi.distage.roles

import cats.effect.LiftIO
import distage.{DIResourceBase, DefaultModule, DefaultModule2, Injector, Module, TagK, TagKK}
import izumi.distage.plugins.PluginConfig
import izumi.distage.roles.RoleAppMain.{AdditionalRoles, ArgV}
import izumi.distage.roles.launcher.AppShutdownStrategy._
import izumi.distage.roles.launcher.{AppFailureHandler, AppShutdownStrategy, PreparedApp}
import izumi.functional.bio._
import izumi.fundamentals.platform.cli.model.raw.RawRoleParams
import izumi.fundamentals.platform.cli.model.schema.ParserDef
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.language.unused
import izumi.fundamentals.platform.resources.IzArtifactMaterializer

import scala.concurrent.ExecutionContext

abstract class RoleAppMain[F[_]: TagK: DefaultModule](implicit artifact: IzArtifactMaterializer) {
  protected def pluginConfig: PluginConfig
  protected def shutdownStrategy: AppShutdownStrategy[F]

  def main(args: Array[String]): Unit = {
    val argv = ArgV(args)
    try {
      val appModule = makeAppModule(argv)
      val overrideModule = makeAppModuleOverride(argv)
      Injector.NoProxies[Identity]().produceRun(appModule.overridenBy(overrideModule)) {
        appResource: Lifecycle[Identity, PreparedApp[F]] =>
          appResource.use(_.run())
      }
    } catch {
      case t: Throwable =>
        createEarlyFailureHandler(argv).onError(t)
    }
  }

  protected def requiredRoles(@unused args: ArgV): Vector[RawRoleParams] = {
    Vector.empty
  }

  protected def makeAppModuleOverride(@unused args: ArgV): Module = {
    Module.empty
  }

  protected def makeAppModule(args: ArgV): Module = {
    new MainAppModule[F](
      args = args,
      additionalRoles = AdditionalRoles(requiredRoles(args)),
      shutdownStrategy = shutdownStrategy,
      pluginConfig = pluginConfig,
      appArtifact = artifact.get,
    )
  }

  protected def createEarlyFailureHandler(@unused args: ArgV): AppFailureHandler = {
    AppFailureHandler.TerminatingHandler
  }
}

object RoleAppMain {

  abstract class LauncherF[F[_]: TagK: LiftIO: DefaultModule](
    shutdownExecutionContext: ExecutionContext = ExecutionContext.global
  )(implicit artifact: IzArtifactMaterializer
  ) extends RoleAppMain[F] {
    override protected def shutdownStrategy: AppShutdownStrategy[F] = new CatsEffectIOShutdownStrategy(shutdownExecutionContext)
  }

  abstract class LauncherBIO[F[+_, +_]: TagKK: BIOAsync: DefaultModule2](implicit artifact: IzArtifactMaterializer) extends RoleAppMain[F[Throwable, ?]] {
    override protected def shutdownStrategy: AppShutdownStrategy[F[Throwable, ?]] = new BIOShutdownStrategy[F]
  }

  abstract class LauncherIdentity(implicit artifact: IzArtifactMaterializer) extends RoleAppMain[Identity] {
    override protected def shutdownStrategy: AppShutdownStrategy[Identity] = new JvmExitHookLatchShutdownStrategy
  }

  final case class ArgV(args: Array[String])
  final case class AdditionalRoles(knownRequiredRoles: Vector[RawRoleParams])

  object Options extends ParserDef {
    final val logLevelRootParam = arg("log-level-root", "ll", "root log level", "{trace|debug|info|warn|error|critical}")
    final val logFormatParam = arg("log-format", "lf", "log format", "{hocon|json}")
    final val configParam = arg("config", "c", "path to config file", "<path>")
    final val dumpContext = flag("debug-dump-graph", "dump DI graph for debugging")
    final val use = arg("use", "u", "activate a choice on functionality axis", "<axis>:<choice>")
  }
}
