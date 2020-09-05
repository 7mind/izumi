package izumi.distage.roles

import distage._
import izumi.distage.plugins.PluginConfig
import izumi.distage.roles.RoleAppMain.{AdditionalRoles, ArgV}
import izumi.distage.roles.launcher.AppShutdownStrategy
import izumi.distage.roles.launcher.services.AppFailureHandler
import izumi.distage.roles.launcher.services.StartupPlanExecutor.PreparedApp
import izumi.fundamentals.platform.cli.model.raw.RawRoleParams
import izumi.fundamentals.platform.cli.model.schema.ParserDef
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.language.unused
import izumi.reflect.Tag

abstract class RoleAppMain[F[_]: TagK]()(implicit t: Tag[TagK[F]]) {
  def main(args: Array[String]): Unit = {
    try {
      val argv = ArgV(args)
      val appModule = makeAppModule(argv)
      val overrideModule = makeAppModuleOverride(argv)
      Injector.NoProxies().produceRun(appModule.overridenBy(overrideModule)) {
        appResource: DIResourceBase[Identity, PreparedApp[F]] =>
          appResource.use(_.run())
      }
    } catch {
      case t: Throwable =>
        createEarlyFailureHandler().onError(t)
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
      args,
      AdditionalRoles(requiredRoles(args)),
      makeShutdownStrategy(),
      makePluginConfig(),
    )
  }

  protected def createEarlyFailureHandler(): AppFailureHandler = {
    AppFailureHandler.TerminatingHandler
  }

  @deprecated("to remove", "04/09/2020")
  protected def makeShutdownStrategy(): AppShutdownStrategy[F]

  protected def makePluginConfig(): PluginConfig

}

object RoleAppMain {
  case class ArgV(args: Array[String])
  case class AdditionalRoles(knownRequiredRoles: Vector[RawRoleParams])

  object Options extends ParserDef {
    final val logLevelRootParam = arg("log-level-root", "ll", "root log level", "{trace|debug|info|warn|error|critical}")
    final val logFormatParam = arg("log-format", "lf", "log format", "{hocon|json}")
    final val configParam = arg("config", "c", "path to config file", "<path>")
    final val dumpContext = flag("debug-dump-graph", "dump DI graph for debugging")
    final val use = arg("use", "u", "activate a choice on functionality axis", "<axis>:<choice>")
  }
}
