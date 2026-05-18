package izumi.distage.roles

import distage.Injector
import izumi.distage.framework.services.ModuleProvider
import izumi.distage.framework.{PlanCheckConfig, PlanCheckMaterializer, RoleCheckableApp}
import izumi.distage.model.Locator
import izumi.distage.model.definition.{Axis, Module, ModuleDef}
import izumi.distage.modules.DefaultModule
import izumi.distage.plugins.PluginConfig
import izumi.distage.roles.RoleAppMain.ArgV
import izumi.distage.roles.launcher.AppResourceProvider.AppResource
import izumi.distage.roles.launcher.{AppFailureHandler, AppShutdownStrategy}
import izumi.functional.lifecycle.Lifecycle
import izumi.functional.bio.{Bifunctorized, IO2, Primitives2}
import izumi.functional.bio.data.Morphism2
import izumi.fundamentals.platform.IzPlatform
import izumi.fundamentals.platform.cli.model.schema.ParserDef
import izumi.fundamentals.platform.cli.model.{RequiredRoles, RoleArgs}
import izumi.fundamentals.platform.resources.IzArtifactMaterializer
import izumi.logstage.distage.LogIO2Module
import izumi.reflect.TagKK

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
abstract class RoleAppMain[F[+_, +_]](
  implicit
  override val tagK: TagKK[F],
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
  protected def shutdownStrategy: AppShutdownStrategy[F] = RoleAppMainPlatformSpecific.defaultShutdownStrategy[F]

  /**
    * Overrides applied to [[roleAppBootModule]]
    *
    * @see [[izumi.distage.roles.RoleAppBootModule]] for initial values of [[roleAppBootModule]]
    *
    * @note Role App Bootstrap always runs under [[Bifunctorized.IdentityBifunctorized]], other effects (cats.effect.IO, zio.IO) are not available at this stage.
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
  protected def requiredRoles(@unused argv: ArgV): Vector[RoleArgs] = Vector.empty

  def main(args: Array[String]): RoleAppMainPlatformSpecific.MainEffect[Unit] = {
    val argv = ArgV(args)
    try {
      Injector.NoProxies[Bifunctorized.IdentityBifunctorized]().produceRun(roleAppBootModule(argv)) {
        (appResource: AppResource[F]) =>
          appResource.resource.use(_.run())
      }
    } catch {
      case t: Throwable =>
        earlyFailureHandler(argv).onError(t)
        RoleAppMainPlatformSpecific.failedMain(t)
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
  def replLocator(args: String*)(implicit F: IO2[F], P: Primitives2[F]): F[Throwable, Locator] = {
    F.map(replLocatorWithClose(args*))(_._1)
  }

  def replLocatorWithClose(args: String*)(implicit F: IO2[F], P: Primitives2[F]): F[Throwable, (Locator, () => F[Nothing, Unit])] = {
    // Identity bootstrap evaluates synchronously and re-suspends inside the target F via `sync` /
    // `syncThrowable`. Each Identity-flavored Lifecycle is lifted via `mapK` over this Morphism2.
    val identityToF: Morphism2[Bifunctorized.IdentityBifunctorized, F] = new Morphism2.Instance[Bifunctorized.IdentityBifunctorized, F] {
      override def apply[E, A](ib: Bifunctorized.IdentityBifunctorized[E, A]): F[E, A] = {
        F.syncThrowable(Bifunctorized.debifunctorizeIdentity(ib.asInstanceOf[Bifunctorized.IdentityBifunctorized[Throwable, A]]))
          .asInstanceOf[F[E, A]]
      }
    }

    val combinedLifecycle: Lifecycle[F, Throwable, Locator] = {
      Injector
        .NoProxies[Bifunctorized.IdentityBifunctorized]()
        .produceGet[AppResource[F]](roleAppBootModule(ArgV(args.toArray)))
        .mapK[Bifunctorized.IdentityBifunctorized, F](identityToF)
        .flatMap(_.resource.mapK[Bifunctorized.IdentityBifunctorized, F](identityToF))
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
    ) ++ new RoleAppBootArgsModule(
      args = argv,
      requiredRoles = additionalRoles,
    )
  }

  protected def earlyFailureHandler(@unused args: ArgV): AppFailureHandler = {
    RoleAppMainPlatformSpecific.defaultEarlyFailureHandler
  }
}

object RoleAppMain {

  abstract class LauncherBIO[F[+_, +_]: TagKK: DefaultModule](implicit artifact: IzArtifactMaterializer) extends RoleAppMain[F] {
    // LogIO2[F] is already available via ModuleProvider.appModules.LogIO2Module[F]() in `RoleAppBootModule`
  }

  type LauncherCats[F[_]] = RoleAppMain[Bifunctorized[F, +_, +_]]

  type Launcher1[F[_]] = RoleAppMain[Bifunctorized[F, +_, +_]]

  abstract class LauncherIdentity(implicit artifact: IzArtifactMaterializer) extends RoleAppMain[Bifunctorized.IdentityBifunctorized] {
    override protected def shutdownStrategy: AppShutdownStrategy[Bifunctorized.IdentityBifunctorized] = {
      RoleAppMainPlatformSpecific.defaultIdentityShutdownStrategy
    }
  }

  final case class ArgV(args: Array[String])
  object ArgV {
    def empty: ArgV = ArgV(Array.empty)
  }

  object Options extends ParserDef {
    final val logLevelRootParam = arg("log-level-root", "ll", "root log level", "{trace|debug|info|warn|error|critical}")
    final val logFormatParam = arg("log-format", "lf", "log format", "{text|json}")
    final val ignoreAllReferenceConfigs = if (IzPlatform.isScalaJS) None else Some(flag("ignore-all-reference-configs", "nc", "ignore all bundled reference configs"))
    final val configParam = if (IzPlatform.isScalaJS) None else Some(arg("config", "c", "path to config file", "<path>"))
    final val dumpContext = if (IzPlatform.isScalaJS) None else Some(flag("debug-dump-graph", "dump DI graph for debugging"))
    final val use = arg("use", "u", "activate a choice on functionality axis", "<axis>:<choice>")
  }
}
