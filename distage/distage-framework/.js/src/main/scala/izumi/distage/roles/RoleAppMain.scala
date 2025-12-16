package izumi.distage.roles

import distage.Injector
import izumi.distage.model.Locator
import izumi.distage.model.definition.{Axis, Module}
import izumi.distage.modules.DefaultModule
import izumi.distage.plugins.PluginConfig
import izumi.distage.roles.RoleAppMain.ArgV
import izumi.distage.roles.launcher.AppResourceProvider.AppResource
import izumi.distage.roles.launcher.{AppFailureHandler, AppShutdownStrategy}
import izumi.functional.lifecycle.Lifecycle
import izumi.functional.quasi.QuasiIO
import izumi.fundamentals.platform.cli.model.schema.ParserDef
import izumi.fundamentals.platform.cli.model.{RequiredRoles, RoleArgs}
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.resources.IzArtifactMaterializer
import izumi.reflect.TagK

import scala.annotation.unused
import scala.concurrent.Future

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
  val tagK: TagK[F],
  val quasi: QuasiIO[F],
  val defaultModule: DefaultModule[F],
  val artifact: IzArtifactMaterializer,
) {

  protected def pluginConfig: PluginConfig
  protected def bootstrapPluginConfig: PluginConfig = PluginConfig.empty
  /**
    * Allow to set these axis choices in config even if they're not used in the application
    * Normally, an axis choice specified in config, but never used would be deemed an error.
    */
  protected def unusedValidAxisChoices: Set[Axis.AxisChoice] = Set.empty
  protected def shutdownStrategy: AppShutdownStrategy[F] = new AppShutdownStrategy.ImmediateExitShutdownStrategy[F]()

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
  protected def requiredRoles(@unused argv: ArgV): Vector[RoleArgs] = Vector.empty

  def main(args: Array[String]): Future[Unit] = {
    val argv = ArgV(args)
    try {
      Injector.NoProxies[Identity]().produceRun(roleAppBootModule(argv)) {
        (appResource: AppResource[F]) =>
          appResource.resource.use(_.run())
      }
    } catch {
      case t: Throwable =>
        earlyFailureHandler(argv).onError(t)
        Future.failed(t)
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
    F.map(replLocatorWithClose(args*)(using F))(_._1)
  }

  def replLocatorWithClose(args: String*)(implicit F: QuasiIO[F]): F[(Locator, () => F[Unit])] = {
    val combinedLifecycle: Lifecycle[F, Locator] = {
      Injector
        .NoProxies[Identity]()
        .produceGet[AppResource[F]](roleAppBootModule(ArgV(args.toArray))).toEffect[F](using F)
        .flatMap(_.resource.toEffect[F](using F))(using F)
        .flatMap(_.appResource)(using F)
    }
    combinedLifecycle.unsafeAllocate()(using F)
  }

  final def roleAppBootModule: Module = {
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
    AppFailureHandler.NullHandler
  }
}

object RoleAppMain {

  final case class ArgV(args: Array[String])
  object ArgV {
    def empty: ArgV = ArgV(Array.empty)
  }

  object Options extends ParserDef {
    final val logLevelRootParam = arg("log-level-root", "ll", "root log level", "{trace|debug|info|warn|error|critical}")
    final val logFormatParam = arg("log-format", "lf", "log format", "{text|json}")
    final val use = arg("use", "u", "activate a choice on functionality axis", "<axis>:<choice>")
  }
}
