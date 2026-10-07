package izumi.distage.roles.test

import distage.{DefaultModule, Lifecycle, Module, ModuleDef, TagK}
import izumi.distage.model.Locator
import izumi.distage.config.ConfigModuleDef
import izumi.distage.plugins.{PluginConfig, PluginDef}
import izumi.distage.roles.RoleAppMain
import izumi.distage.roles.RoleAppMain.ArgV
import izumi.distage.roles.bundled.{BundledRolesModule, ConfigWriter, RunAllTasks}
import izumi.distage.roles.launcher.{AppFailureHandler, AppShutdownStrategy, PreparedApp, RoleAppEntrypoint}
import izumi.distage.roles.launcher.AppResourceProvider.AppResource
import izumi.distage.roles.model.definition.RoleModuleDef
import izumi.distage.roles.model.{RoleDescriptor, RoleTask}
import izumi.functional.quasi.{QuasiAsync, QuasiIO, QuasiIORunner}
import izumi.fundamentals.platform.cli.model.{EntrypointArgs, RoleArgs}
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.language.Quirks.Discarder
import izumi.fundamentals.platform.versions.Version
import izumi.distage.testkit.runner.spec.AnyWordSpec

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}
import scala.annotation.unused

final class FrameworkNativeTest extends AnyWordSpec {
  "Native role launcher" should {
    "run a self-contained application resource without adding a shutdown strategy root" in {
      val stage = new AtomicInteger(0)
      val entrypoint = new RoleAppEntrypoint[Identity] {
        override def runTasksAndRoles(@unused locator: Locator, @unused effect: QuasiIO[Identity], @unused effectAsync: QuasiAsync[Identity]): Unit = {
          require(stage.incrementAndGet() == 1)
        }
      }
      val prepared = PreparedApp[Identity](
        Lifecycle.makeSimple[Locator](Locator.empty)(_ => require(stage.incrementAndGet() == 2)),
        entrypoint,
        QuasiIORunner[Identity],
        QuasiIO[Identity],
        QuasiAsync[Identity],
      )
      val resource = AppResource[Identity](Lifecycle.makeSimple(prepared)(_ => require(stage.incrementAndGet() == 3)))
      val main = new RoleAppMain.LauncherIdentity {
        override protected def pluginConfig: PluginConfig = PluginConfig.empty
        override protected def earlyFailureHandler(@unused argv: ArgV): AppFailureHandler = AppFailureHandler.NullHandler
        override protected def roleAppBootOverrides(argv: ArgV): Module = super.roleAppBootOverrides(argv) ++ new ModuleDef {
          make[AppResource[Identity]].fromResource(Lifecycle.makeSimple(resource)(_ => require(stage.incrementAndGet() == 4)))
          make[AppShutdownStrategy[Identity]].from {
            () => throw new IllegalStateException("Unused shutdown strategy must not be provisioned")
          }
        }
      }
      main.main(Array.empty)
      assert(stage.get() == 4)
    }

    "execute an Identity task with file configuration and close its resource" in runTask[Identity](NativeFrameworkTask.id, bundledRoles = false)
    "execute a Cats IO task with file configuration and close its resource" in runTask[cats.effect.IO](NativeFrameworkTask.id, bundledRoles = false)
    "execute a ZIO task with file configuration and close its resource" in runTask[zio.Task](NativeFrameworkTask.id, bundledRoles = false)
    "run only custom tasks through the bundled task aggregate" in runTask[Identity](RunAllTasks.id, bundledRoles = true)
    "fail explicitly when the unavailable config writer is requested" in {
      val error = intercept[UnsupportedOperationException](runTask[Identity](ConfigWriter.id, bundledRoles = true))
      assert(error.getMessage.contains("HOCON and derived configuration schemas"))
    }
  }

  private def runTask[F[_]: TagK: DefaultModule](role: String, bundledRoles: Boolean): Unit = {
    val record = new NativeRoleRecord
    val makePlugin = (version: Version) => new PluginDef with RoleModuleDef with ConfigModuleDef {
      if (bundledRoles) include(new BundledRolesModule[F](version))
      makeRole[NativeFrameworkTask[F]]
      make[NativeRoleRecord].fromValue(record)
      make[NativeRoleCloseable]
      makeConfig[NativeTaskConfig]("nativeTask")
    }
    val config = Files.createTempFile("distage-native-role-", ".json")
    try {
      Files.writeString(config, """{"nativeTask":{"label":"héllo","number":42}}""", StandardCharsets.UTF_8).discard()
      val main = new RoleAppMain[F] {
        override protected def pluginConfig: PluginConfig = PluginConfig.const(Seq(makePlugin(artifact.get.version.version)))
        override protected def requiredRoles(@unused argv: ArgV): Vector[RoleArgs] = Vector(RoleArgs(role))
        override protected def earlyFailureHandler(@unused argv: ArgV): AppFailureHandler = AppFailureHandler.NullHandler
        override protected def roleAppBootOverrides(argv: ArgV): Module = super.roleAppBootOverrides(argv) ++ new ModuleDef {
          make[Boolean].named("distage.roles.logs.static-log-router").fromValue(false)
        }
      }
      main.main(Array("-c", config.toString))
      assert(record.tasks.get() == 1)
      assert(record.acquisitions.get() == 1)
      assert(record.releases.get() == 1)
      assert(record.config.get().contains(NativeTaskConfig("héllo", 42))).discard()
    } finally Files.delete(config)
  }
}

final case class NativeTaskConfig(label: String, number: Int)

final class NativeRoleRecord {
  val tasks = new AtomicInteger(0)
  val acquisitions = new AtomicInteger(0)
  val releases = new AtomicInteger(0)
  val config = new AtomicReference(Option.empty[NativeTaskConfig])
}

final class NativeRoleCloseable(record: NativeRoleRecord) extends AutoCloseable {
  record.acquisitions.incrementAndGet().discard()
  override def close(): Unit = record.releases.incrementAndGet().discard()
}

final class NativeFrameworkTask[F[_]](config: NativeTaskConfig, record: NativeRoleRecord, closeable: NativeRoleCloseable, F: QuasiIO[F]) extends RoleTask[F] {
  override def start(@unused roleParameters: EntrypointArgs): F[Unit] = F.maybeSuspend {
    closeable.discard()
    require(record.acquisitions.get() == 1 && record.releases.get() == 0)
    record.config.set(Some(config))
    record.tasks.incrementAndGet().discard()
  }
}

object NativeFrameworkTask extends RoleDescriptor {
  override final val id = "native-framework-task"
}
