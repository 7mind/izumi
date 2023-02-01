package izumi.distage.roles.test

import distage.ModuleDef
import izumi.distage.model.definition.Module
import izumi.distage.plugins.{PluginConfig, PluginDef}
import izumi.distage.roles.model.definition.RoleModuleDef
import izumi.distage.roles.model.{RoleDescriptor, RoleTask}
import izumi.distage.roles.test.RoleAppMain.ArgV
import izumi.functional.bio.UnsafeRun2
import izumi.functional.quasi.QuasiIO
import izumi.fundamentals.platform.cli.model.raw.{RawAppArgs, RawEntrypointParams, RawRoleParams}
import izumi.logstage.api.IzLogger
import org.scalatest.wordspec.AsyncWordSpec
import zio.internal.Platform

import scala.concurrent.ExecutionContext

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
  "distage-framework" should {
    "run on cats.effect.IO" in {

      val main = new RoleAppMain[cats.effect.IO]() {
        override protected def pluginConfig: PluginConfig = PluginConfig.const(
          Seq(
            new PluginDef with RoleModuleDef {
              makeRole[TestTask00[cats.effect.IO]]
              make[ExecutionContext].named("cpu").fromValue(executionContext)
            }
          )
        )

        override protected def roleAppBootOverrides(argv: ArgV): Module = new ModuleDef {
          make[RawAppArgs].fromValue(RawAppArgs(RawEntrypointParams.empty, Vector(RawRoleParams("testtask00"))))
        }
      }
      for {
        _ <- main.main()
      } yield {
        assert(true)
      }
    }

    "run on zio.Task" in {
      val platform = Platform.fromExecutionContext(executionContext)
      val r = UnsafeRun2.createZIO[Any](platform, ())

      val main = new RoleAppMain[zio.Task]() {
        override protected def pluginConfig: PluginConfig = PluginConfig.const(
          Seq(
            new PluginDef with RoleModuleDef {
              makeRole[TestTask00[zio.Task]]
              make[ExecutionContext].named("zio.cpu").from(executionContext)

              make[UnsafeRun2[zio.IO]].fromValue(r)
              make[Platform].fromValue(platform)
              make[zio.Runtime[Any]].fromValue(r.runtime)
            }
          )
        )

        override protected def roleAppBootOverrides(argv: ArgV): Module = new ModuleDef {
          make[RawAppArgs].fromValue(RawAppArgs(RawEntrypointParams.empty, Vector(RawRoleParams("testtask00"))))
        }
      }
      for {
        _ <- main.main()
      } yield {
        assert(true)
      }
    }
  }
}
