package izumi.distage.roles.test

import distage.Injector
import izumi.distage.model.definition.{Axis, Module}
import izumi.distage.modules.DefaultModule
import izumi.distage.plugins.PluginConfig
import izumi.distage.roles.RoleAppBootModule
import izumi.distage.roles.launcher.AppResourceProvider.AppResource
import izumi.distage.roles.launcher.{AppFailureHandler, AppShutdownStrategy}
import izumi.distage.roles.test.RoleAppMain.ArgV
import izumi.functional.quasi.QuasiIO
import izumi.fundamentals.platform.cli.model.raw.{RawRoleParams, RequiredRoles}
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
          appResource.resource.use(_.run())
      }
    } catch {
      case t: Throwable =>
        // Future(earlyFailureHandler(argv).onError(t))
        throw t
    }
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
  def roleAppBootModule(@unused argv: ArgV, @unused additionalRoles: RequiredRoles): Module = {
    new RoleAppBootModule[F](
      shutdownStrategy = shutdownStrategy,
      pluginConfig = pluginConfig,
      bootstrapPluginConfig = bootstrapPluginConfig,
      appArtifact = artifact.get,
      unusedValidAxisChoices,
    ) /*++ new RoleAppBootArgsModule(
      args = argv,
      requiredRoles = additionalRoles,
    )*/
  }

  protected def earlyFailureHandler(@unused args: ArgV): AppFailureHandler = {
    AppFailureHandler.NullHandler
  }

}

object RoleAppMain {

  case class ArgV()

  object ArgV {
    def empty: ArgV = ArgV()
  }
}
