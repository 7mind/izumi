package izumi.distage.roles

import cats.effect.LiftIO
import distage.Injector
import izumi.distage.framework.services.ModuleProvider
import izumi.distage.framework.{PlanCheckConfig, PlanCheckMaterializer, RoleCheckableApp}
import izumi.distage.model.definition.{Module, ModuleDef}
import izumi.distage.modules.{DefaultModule, DefaultModule2, DefaultModule3}
import izumi.distage.plugins.PluginConfig
import izumi.distage.roles.RoleAppMain.{ArgV, RequiredRoles}
import izumi.distage.roles.launcher.AppResourceProvider.AppResource
import izumi.distage.roles.launcher.AppShutdownStrategy._
import izumi.distage.roles.launcher.{AppFailureHandler, AppShutdownStrategy}
import izumi.functional.bio.{Async2, Async3}
import izumi.fundamentals.platform.cli.model.raw.RawRoleParams
import izumi.fundamentals.platform.cli.model.schema.ParserDef
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.language.{open, unused}
import izumi.fundamentals.platform.resources.IzArtifactMaterializer
import izumi.logstage.distage.{LogIO2Module, LogIO3Module}
import izumi.reflect.{TagK, TagK3, TagKK}

import scala.concurrent.ExecutionContext

/**
  * Create a launcher for role-based applications by extending this in a top-level object
  *
  * @example
  *
  * {{{
  * import izumi.distage.framework.RoleAppMain
  * import izumi.distage.plugins.PluginConfig
  *
  * object RoleLauncher extends RoleAppMain.LauncherBIO[zio.IO] {
  *
  *   override def pluginConfig: PluginConfig = {
  *     PluginConfig.cached(pluginsPackage = "my.example.app.plugins")
  *   }
  *
  * }
  * }}}
  *
  * @see [[https://izumi.7mind.io/distage/distage-framework#roles Roles]]
  * @see [[https://izumi.7mind.io/distage/distage-framework#plugins Plugins]]
  */
abstract class RoleAppMain[F[_]](
  implicit
  override val tagK: TagK[F],
  val defaultModule: DefaultModule[F],
  val artifact: IzArtifactMaterializer,
) extends RoleCheckableApp[F] { self =>

  protected def pluginConfig: PluginConfig
  protected def bootstrapPluginConfig: PluginConfig = PluginConfig.empty
  protected def shutdownStrategy: AppShutdownStrategy[F]

  /**
    * @note The components added here are visible during the creation of the app, not *inside* the app,
    *       to add components *inside* the app, add a mutator for the component `Module @Id("roleapp")`,
    *       example:
    *
    *       {{{
    *       override def roleAppBootOverrides(@unused argv: ArgV): Module = super.roleAppBootOverrides(argv) ++ new ModuleDef {
    *         modify[Module].named("roleapp")(_ ++ new ModuleDef {
    *           make[MyComponentX](
    *         })
    *       }
    *       }}}
    */
  protected def roleAppBootOverrides(@unused argv: ArgV): Module = Module.empty

  /** Roles always enabled in this [[RoleAppMain]] */
  protected def requiredRoles(@unused argv: ArgV): Vector[RawRoleParams] = Vector.empty

  def main(args: Array[String]): Unit = {
    val argv = ArgV(args)
    try {
      Injector.NoProxies[Identity]().produceRun(roleAppBootModule(argv)) {
        appResource: AppResource[F] =>
          appResource.runApp()
      }
    } catch {
      case t: Throwable =>
        earlyFailureHandler(argv).onError(t)
    }
  }

  /**
    * Shortcut for [[izumi.distage.framework.PlanCheck.Main]]
    *
    * {{{
    * object WiringTest extends MyApp.PlanCheck(PlanCheckConfig(...))
    * }}}
    *
    * same as
    *
    * {{{
    * object WiringTest extends PlanCheck.Main(MyApp, PlanCheckConfig(...))
    * }}}
    */
  @open class PlanCheck[Cfg <: PlanCheckConfig.Any](cfg: Cfg = PlanCheckConfig.empty)(implicit planCheck: PlanCheckMaterializer[self.type, Cfg])
    extends izumi.distage.framework.PlanCheck.Main[self.type, Cfg](self, cfg)

  /** @see [[izumi.distage.framework.PlanCheck.assertAppCompileTime]] */
  def assertAppCompileTime[Cfg <: PlanCheckConfig.Any](
    cfg: Cfg = PlanCheckConfig.empty
  )(implicit planCheck: PlanCheckMaterializer[self.type, Cfg]
  ): PlanCheckMaterializer[self.type, Cfg] = {
    izumi.distage.framework.PlanCheck.assertAppCompileTime[self.type, Cfg](self, cfg)
  }

  override final def roleAppBootModule: Module = {
    roleAppBootModule(ArgV.empty)
  }

  def roleAppBootModule(argv: ArgV): Module = {
    val mainModule = roleAppBootModule(argv, RequiredRoles(requiredRoles(argv)))
    val overrideModule = roleAppBootOverrides(argv)
    mainModule overriddenBy overrideModule
  }

  def roleAppBootModule(argv: ArgV, additionalRoles: RequiredRoles): Module = {
    new RoleAppBootModule[F](
      args = argv,
      requiredRoles = additionalRoles,
      shutdownStrategy = shutdownStrategy,
      pluginConfig = pluginConfig,
      bootstrapPluginConfig = bootstrapPluginConfig,
      appArtifact = artifact.get,
    )
  }

  protected def earlyFailureHandler(@unused args: ArgV): AppFailureHandler = {
    AppFailureHandler.TerminatingHandler
  }
}

object RoleAppMain {

  abstract class LauncherBIO2[F[+_, +_]: TagKK: Async2: DefaultModule2](implicit artifact: IzArtifactMaterializer) extends RoleAppMain[F[Throwable, ?]] {
    override protected def shutdownStrategy: AppShutdownStrategy[F[Throwable, ?]] = new BIOShutdownStrategy[F]

    // add LogIO2[F] for bifunctor convenience to match existing LogIO[F[Throwable, ?]]
    override protected def roleAppBootOverrides(argv: ArgV): Module = super.roleAppBootOverrides(argv) ++ new ModuleDef {
      modify[ModuleProvider](_.mapApp(LogIO2Module[F]() +: _))
    }
  }

  abstract class LauncherBIO3[F[-_, +_, +_]: TagK3: Async3: DefaultModule3](implicit artifact: IzArtifactMaterializer) extends RoleAppMain[F[Any, Throwable, ?]] {
    override protected def shutdownStrategy: AppShutdownStrategy[F[Any, Throwable, ?]] = new BIOShutdownStrategy[F[Any, +?, +?]]

    // add LogIO2[F] for trifunctor convenience to match existing LogIO[F[Throwable, ?]]
    override protected def roleAppBootOverrides(argv: ArgV): Module = super.roleAppBootOverrides(argv) ++ new ModuleDef {
      modify[ModuleProvider](_.mapApp(LogIO3Module[F]() +: _))
    }
  }

  abstract class LauncherCats[F[_]: TagK: LiftIO: DefaultModule](
    shutdownExecutionContext: ExecutionContext = ExecutionContext.global
  )(implicit artifact: IzArtifactMaterializer
  ) extends RoleAppMain[F] {
    override protected def shutdownStrategy: AppShutdownStrategy[F] = new CatsEffectIOShutdownStrategy(shutdownExecutionContext)
  }

  abstract class LauncherIdentity(implicit artifact: IzArtifactMaterializer) extends RoleAppMain[Identity] {
    override protected def shutdownStrategy: AppShutdownStrategy[Identity] = new JvmExitHookLatchShutdownStrategy
  }

  final case class ArgV(args: Array[String])
  object ArgV {
    def empty: ArgV = ArgV(Array.empty)
  }

  final case class RequiredRoles(requiredRoles: Vector[RawRoleParams])

  object Options extends ParserDef {
    final val logLevelRootParam = arg("log-level-root", "ll", "root log level", "{trace|debug|info|warn|error|critical}")
    final val logFormatParam = arg("log-format", "lf", "log format", "{hocon|json}")
    final val configParam = arg("config", "c", "path to config file", "<path>")
    final val dumpContext = flag("debug-dump-graph", "dump DI graph for debugging")
    final val use = arg("use", "u", "activate a choice on functionality axis", "<axis>:<choice>")
  }
}
