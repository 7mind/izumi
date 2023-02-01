package izumi.distage.roles.test

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import distage.{BootstrapModule, Id, Injector, ModuleBase, ModuleDef}
import izumi.distage.framework.services.ModuleProvider
import izumi.distage.model.definition.{Axis, Module}
import izumi.distage.modules.DefaultModule
import izumi.distage.plugins.{PluginConfig, PluginDef}
import izumi.distage.roles.RoleAppBootModule
import izumi.distage.roles.launcher.AppResourceProvider.AppResource
import izumi.distage.roles.launcher.{AppFailureHandler, AppShutdownStrategy}
import izumi.distage.roles.model.definition.RoleModuleDef
import izumi.distage.roles.model.{RoleDescriptor, RoleTask}
import izumi.distage.roles.test.RoleAppMain.ArgV
import izumi.functional.quasi.QuasiIO
import izumi.fundamentals.platform.cli.model.raw.{RawAppArgs, RawEntrypointParams, RawRoleParams, RequiredRoles}
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.resources.IzArtifactMaterializer
import izumi.logstage.api.IzLogger
import izumi.reflect.TagK
import org.scalatest.wordspec.AsyncWordSpec

import scala.annotation.unused
import scala.concurrent.{ExecutionContext, Future}

object RoleAppMain {

  case class ArgV()

  object ArgV {
    def empty: ArgV = ArgV()
  }
}

abstract class RoleAppMain[F[_]](
  implicit
  val tagK: TagK[F],
  val defaultModule: DefaultModule[F],
  val artifact: IzArtifactMaterializer,
) {

  protected def pluginConfig: PluginConfig
  protected def bootstrapPluginConfig: PluginConfig = PluginConfig.empty
  protected def unusedValidAxisChoices: Set[Axis.AxisChoice] = Set.empty
  protected def shutdownStrategy: AppShutdownStrategy[F]

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

class TestTask00[F[_]: QuasiIO](logger: IzLogger) extends RoleTask[F] {
  override def start(roleParameters: RawEntrypointParams, freeArgs: Vector[String]): F[Unit] = {
    QuasiIO[F].maybeSuspend {
      logger.info(s"[TestTask00] Entrypoint invoked!: $roleParameters, $freeArgs")
    }
  }
}

object TestTask00 extends RoleDescriptor {
  override final val id = "testtask00"
}

class BaseFrameworkTest extends AsyncWordSpec {
  implicit val rt: IORuntime = IORuntime.builder().setCompute(implicitly, () => ()).setBlocking(implicitly, () => ()).build()

  "distage-framework" should {
    "run" in {

      val main = new RoleAppMain[cats.effect.IO]() {
        override protected def pluginConfig: PluginConfig = PluginConfig.const(new PluginDef with RoleModuleDef {
          makeRole[TestTask00[cats.effect.IO]]
        })

        override protected def roleAppBootOverrides(argv: ArgV): Module = new ModuleDef {
          make[RawAppArgs].fromValue(RawAppArgs(RawEntrypointParams.empty, Vector(RawRoleParams("testtask00"))))

          make[BootstrapModule].named("roleapp").from {
            (provider: ModuleProvider, bsModule: ModuleBase @Id("bootstrap")) =>
              (provider.bootstrapModules().merge overriddenBy bsModule) overriddenBy new ModuleDef {
                make[IORuntime].fromValue(rt)
                make[ExecutionContext].from(rt.compute)
              }
          }

        }

        override protected def shutdownStrategy: AppShutdownStrategy[IO] = new AppShutdownStrategy.ImmediateExitShutdownStrategy[cats.effect.IO]()
      }
      for {
        _ <- main.main()
      } yield {
        assert(true)
      }
    }
  }
}
