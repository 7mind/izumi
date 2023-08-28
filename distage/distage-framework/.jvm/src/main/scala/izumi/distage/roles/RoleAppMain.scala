package izumi.distage.roles

import cats.effect.kernel.Async
import distage.Injector
import izumi.distage.framework.services.ModuleProvider
import izumi.distage.framework.{PlanCheckConfig, PlanCheckMaterializer, RoleCheckableApp}
import izumi.distage.model.Locator
import izumi.distage.model.definition.{Axis, Module, ModuleDef}
import izumi.distage.modules.{DefaultModule, DefaultModule2, DefaultModule3}
import izumi.distage.plugins.PluginConfig
import izumi.distage.roles.RoleAppMain.ArgV
import izumi.distage.roles.launcher.AppResourceProvider.AppResource
import izumi.distage.roles.launcher.AppShutdownStrategy.*
import izumi.distage.roles.launcher.{AppFailureHandler, AppShutdownStrategy}
import izumi.functional.bio.{Async2, Async3}
import izumi.functional.lifecycle.Lifecycle
import izumi.functional.quasi.QuasiIO
import izumi.fundamentals.platform.cli.model.raw.{RawRoleParams, RequiredRoles}
import izumi.fundamentals.platform.cli.model.schema.ParserDef
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.resources.IzArtifactMaterializer
import izumi.logstage.distage.{LogIO2Module, LogIO3Module}
import izumi.reflect.{TagK, TagK3, TagKK}

import scala.annotation.unused

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
) extends RoleCheckableApp[F] {

  protected def pluginConfig: PluginConfig
  protected def bootstrapPluginConfig: PluginConfig = PluginConfig.empty
  /**
    * Allow to set these axis choices in config even if they're not used in the application
    * Normally, an axis choice specified in config, but never used would be deemed an error.
    */
  protected def unusedValidAxisChoices: Set[Axis.AxisChoice] = Set.empty
  protected def shutdownStrategy: AppShutdownStrategy[F]

  /**
    * Overrides applied to [[roleAppBootModule]]
    *
    * @see [[izumi.distage.roles.RoleAppBootModule]] for initial values of [[roleAppBootModule]]
    *
    * @note Bootstrap Injector will always run under Identity, other effects (cats.effect.IO, zio.IO) are not available at this stage.
    *
    * @note The components added here are visible during the creation of the app, but *not inside* the app,
    *       to override components *inside* the app, use `pluginConfig` & [[izumi.distage.plugins.PluginConfig#overriddenBy]]:
    *
    *       {{{
    *       override def pluginConfig: PluginConfig = {
    *         super.pluginConfig overriddenBy new PluginDef {
    *           make[MyComponentX]]
    *         }
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
        (appResource: AppResource[F]) =>
          appResource.resource.use(_.run())
      }
    } catch {
      case t: Throwable =>
        earlyFailureHandler(argv).onError(t)
    }
  }

  /**
    * Create an object graph for inspection in the REPL:
    *
    * {{{
    * scala> val graph = Launcher.replLocator("-u", "mode:test", ":role1")
    * val graph: izumi.fundamentals.platform.functional.Identity[izumi.distage.model.Locator] = izumi.distage.LocatorDefaultImpl@6f6a2ac8
    *
    * scala> val testObj = graph.get[Hello]
    * val testObj: example.Hellower = example.Hellower@25109d84
    *
    * scala> testObj.hello("test")
    * Hello test!
    * }}}
    *
    * @note All resources will be leaked. Use [[replLocatorWithClose]] if you need resource cleanup within a REPL session.
    */

  def replLocator(args: String*)(implicit F: QuasiIO[F]): F[Locator] = {
    F.map(replLocatorWithClose(args*))(_._1)
  }

  def replLocatorWithClose(args: String*)(implicit F: QuasiIO[F]): F[(Locator, () => F[Unit])] = {
    val combinedLifecycle: Lifecycle[F, Locator] = {
      Injector
        .NoProxies[Identity]()
        .produceGet[AppResource[F]](roleAppBootModule(ArgV(args.toArray))).toEffect[F]
        .flatMap(_.resource.toEffect[F])
        .flatMap(_.appResource)
    }
    combinedLifecycle.unsafeAllocate()
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
  open class PlanCheck[Cfg <: PlanCheckConfig.Any](cfg: Cfg = PlanCheckConfig.empty)(implicit planCheck: PlanCheckMaterializer[this.type, Cfg])
    extends izumi.distage.framework.PlanCheck.Main[this.type, Cfg](this, cfg)

  /** @see [[izumi.distage.framework.PlanCheck.assertAppCompileTime]] */
  def assertAppCompileTime[Cfg <: PlanCheckConfig.Any](
    cfg: Cfg = PlanCheckConfig.empty
  )(implicit planCheck: PlanCheckMaterializer[this.type, Cfg]
  ): PlanCheckMaterializer[this.type, Cfg] = {
    izumi.distage.framework.PlanCheck.assertAppCompileTime[this.type, Cfg](this, cfg)
  }

  override final def roleAppBootModule: Module = {
    roleAppBootModule(ArgV.empty)
  }

  def roleAppBootModule(argv: ArgV): Module = {
    val mainModule = roleAppBootModule(argv, RequiredRoles(requiredRoles(argv)))
    val overrideModule = roleAppBootOverrides(argv)
    mainModule overriddenBy overrideModule
  }

  /** @see [[izumi.distage.roles.RoleAppBootModule]] for initial values */
  def roleAppBootModule(argv: ArgV, additionalRoles: RequiredRoles): Module = {
    new RoleAppBootModule[F](
      shutdownStrategy = shutdownStrategy,
      pluginConfig = pluginConfig,
      bootstrapPluginConfig = bootstrapPluginConfig,
      appArtifact = artifact.get,
      unusedValidAxisChoices,
    ) ++ new RoleAppBootArgsModule[F](
      args = argv,
      requiredRoles = additionalRoles,
    )
  }

  protected def earlyFailureHandler(@unused args: ArgV): AppFailureHandler = {
    AppFailureHandler.TerminatingHandler
  }
}

object RoleAppMain {

  abstract class LauncherBIO2[F[+_, +_]: TagKK: Async2: DefaultModule2](implicit artifact: IzArtifactMaterializer) extends RoleAppMain[F[Throwable, _]] {
    override protected def shutdownStrategy: AppShutdownStrategy[F[Throwable, _]] = new BIOShutdownStrategy[F]

    // add LogIO2[F] for bifunctor convenience to match existing LogIO[F[Throwable, _]]
    override protected def roleAppBootOverrides(argv: ArgV): Module = super.roleAppBootOverrides(argv) ++ new ModuleDef {
      modify[ModuleProvider](_.mapApp(LogIO2Module[F]() +: _))
    }
  }

  abstract class LauncherBIO3[F[-_, +_, +_]: TagK3: Async3: DefaultModule3](implicit artifact: IzArtifactMaterializer) extends RoleAppMain[F[Any, Throwable, _]] {
    override protected def shutdownStrategy: AppShutdownStrategy[F[Any, Throwable, _]] = new BIOShutdownStrategy[F[Any, +_, +_]]

    // add LogIO2[F] for trifunctor convenience to match existing LogIO[F[Throwable, _]]
    override protected def roleAppBootOverrides(argv: ArgV): Module = super.roleAppBootOverrides(argv) ++ new ModuleDef {
      modify[ModuleProvider](_.mapApp(LogIO3Module[F]() +: _))
    }
  }

  abstract class LauncherCats[F[_]: TagK: Async: DefaultModule](implicit artifact: IzArtifactMaterializer) extends RoleAppMain[F] {
    override protected def shutdownStrategy: AppShutdownStrategy[F] = new CatsEffectIOShutdownStrategy
  }

  abstract class LauncherIdentity(implicit artifact: IzArtifactMaterializer) extends RoleAppMain[Identity] {
    override protected def shutdownStrategy: AppShutdownStrategy[Identity] = new JvmExitHookLatchShutdownStrategy
  }

  final case class ArgV(args: Array[String])
  object ArgV {
    def empty: ArgV = ArgV(Array.empty)
  }

  object Options extends ParserDef {
    final val logLevelRootParam = arg("log-level-root", "ll", "root log level", "{trace|debug|info|warn|error|critical}")
    final val logFormatParam = arg("log-format", "lf", "log format", "{hocon|json}")
    final val configParam = arg("config", "c", "path to config file", "<path>")
    final val dumpContext = flag("debug-dump-graph", "dump DI graph for debugging")
    final val use = arg("use", "u", "activate a choice on functionality axis", "<axis>:<choice>")
  }
}
