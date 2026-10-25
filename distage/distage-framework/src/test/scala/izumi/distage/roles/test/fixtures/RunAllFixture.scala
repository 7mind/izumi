package izumi.distage.roles.test.fixtures

import izumi.distage.model.definition.Lifecycle
import izumi.distage.plugins.PluginDef
import izumi.distage.roles.bundled.BundledRolesModule
import izumi.distage.roles.model.definition.RoleModuleDef
import izumi.distage.roles.model.{RoleDescriptor, RoleService, RoleTask}
import izumi.functional.quasi.QuasiIO
import izumi.fundamentals.platform.cli.model.EntrypointArgs
import izumi.fundamentals.platform.versions.Version
import izumi.reflect.TagK

import java.util.concurrent.ConcurrentLinkedQueue
import scala.jdk.CollectionConverters.*

object RunAllFixture {
  final class Recorder {
    private val events = new ConcurrentLinkedQueue[String]()
    def record(event: String): Unit = { events.add(event); () }
    def recorded: List[String] = events.asScala.toList
  }

  private def describe(id: String, args: EntrypointArgs): String = s"$id(${args.freeArgs.mkString(",")})"

  sealed abstract class RecordingTask[F[_]: QuasiIO](id: String, recorder: Recorder) extends RoleTask[F] {
    override def start(roleParameters: EntrypointArgs): F[Unit] = QuasiIO[F].maybeSuspend {
      recorder.record(describe(id, roleParameters))
    }
  }

  sealed abstract class RecordingService[F[_]: QuasiIO](id: String, recorder: Recorder) extends RoleService[F] {
    override def start(roleParameters: EntrypointArgs): Lifecycle[F, Unit] = {
      Lifecycle.make(QuasiIO[F].maybeSuspend(recorder.record(s"start ${describe(id, roleParameters)}")))(
        _ => QuasiIO[F].maybeSuspend(recorder.record(s"stop $id"))
      )
    }
  }

  final class TaskA[F[_]: QuasiIO](recorder: Recorder) extends RecordingTask[F](TaskA.id, recorder)
  object TaskA extends RoleDescriptor {
    override final val id = "run-all-task-a"
  }

  final class TaskB[F[_]: QuasiIO](recorder: Recorder) extends RecordingTask[F](TaskB.id, recorder)
  object TaskB extends RoleDescriptor {
    override final val id = "run-all-task-b"
  }

  final class ServiceA[F[_]: QuasiIO](recorder: Recorder) extends RecordingService[F](ServiceA.id, recorder)
  object ServiceA extends RoleDescriptor {
    override final val id = "run-all-service-a"
  }

  final class ServiceB[F[_]: QuasiIO](recorder: Recorder) extends RecordingService[F](ServiceB.id, recorder)
  object ServiceB extends RoleDescriptor {
    override final val id = "run-all-service-b"
  }
}

class RunAllFixturePlugin[F[_]: TagK](recorder: RunAllFixture.Recorder) extends PluginDef with RoleModuleDef {
  include(BundledRolesModule[F](Version.parse("0.0.0")))

  make[RunAllFixture.Recorder].fromValue(recorder)

  makeRole[RunAllFixture.TaskA[F]]
  makeRole[RunAllFixture.TaskB[F]]
  makeRole[RunAllFixture.ServiceA[F]]
  makeRole[RunAllFixture.ServiceB[F]]
}
