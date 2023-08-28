package izumi.distage.roles

import distage.Injector
import izumi.distage.framework.config.PlanningOptions
import izumi.distage.model.Locator
import izumi.distage.model.definition.{Activation, Axis, Module, ModuleDef}
import izumi.distage.modules.DefaultModule
import izumi.distage.plugins.PluginConfig
import izumi.distage.roles.RoleAppMain.ArgV
import izumi.distage.roles.launcher.AppResourceProvider.AppResource
import izumi.distage.roles.launcher.AppShutdownStrategy
import izumi.functional.lifecycle.Lifecycle
import izumi.functional.quasi.QuasiIO
import izumi.fundamentals.platform.cli.model.raw.{RawAppArgs, RawEntrypointParams, RawRoleParams, RequiredRoles}
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.resources.IzArtifactMaterializer
import izumi.reflect.TagK

import scala.annotation.unused
import scala.concurrent.Future

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

  protected def roleAppBootOverrides(@unused argv: ArgV): Module = Module.empty

  /** Roles always enabled in this [[RoleAppMain]] */
  protected def requiredRoles(@unused argv: ArgV): Vector[RawRoleParams] = Vector.empty

  def main(): Future[Unit] = {
    val argv = ArgV()
    try {
      Injector.NoProxies[Identity]().produceRun(roleAppBootModule(argv)) {
        (appResource: AppResource[F]) =>
          appResource.resource.use(r => r.run(): Identity[Future[Unit]])
      }
    } catch {
      case t: Throwable =>
        // Future(earlyFailureHandler(argv).onError(t))
        throw t
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
  def replLocator(argV: ArgV = ArgV.empty): F[Locator] = {
    quasi.map(replLocatorWithClose(argV))(_._1)
  }

  def replLocatorWithClose(argV: ArgV = ArgV.empty): F[(Locator, () => F[Unit])] = {
    val combinedLifecycle: Lifecycle[F, Locator] = {
      Injector
        .NoProxies[Identity]()
        .produceGet[AppResource[F]](roleAppBootModule(argV)).toEffect[F]
        .flatMap(_.resource.toEffect[F])
        .flatMap(_.appResource)
    }
    combinedLifecycle.unsafeAllocate()
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
  def roleAppBootModule(@unused argv: ArgV, additionalRoles: RequiredRoles): Module = {
    new RoleAppBootModule[F](
      shutdownStrategy = shutdownStrategy,
      pluginConfig = pluginConfig,
      bootstrapPluginConfig = bootstrapPluginConfig,
      appArtifact = artifact.get,
      unusedValidAxisChoices,
    ) ++ new ModuleDef {
      make[RawAppArgs].fromValue(RawAppArgs(RawEntrypointParams.empty, additionalRoles.requiredRoles))
      make[PlanningOptions].fromValue(planningOptions())
      make[Activation].named("roleapp").fromValue(activation())
    }
  }

  def planningOptions(): PlanningOptions = PlanningOptions()

  def activation(): Activation = Activation.empty

//  protected def earlyFailureHandler(@unused args: ArgV): AppFailureHandler = {
//    AppFailureHandler.NullHandler
//  }

}

object RoleAppMain {

  case class ArgV()

  object ArgV {
    def empty: ArgV = ArgV()
  }
}
