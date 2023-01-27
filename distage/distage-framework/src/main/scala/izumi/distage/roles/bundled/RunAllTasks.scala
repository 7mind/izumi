package izumi.distage.roles.bundled

import distage.Id
import izumi.distage.roles.model.{RoleDescriptor, RoleTask}
import izumi.functional.quasi.QuasiIO
import izumi.fundamentals.platform.cli.model.raw.RawEntrypointParams
import izumi.fundamentals.platform.cli.model.schema.*

/**
  * This task runs all the non-bundled distage tasks with its arguments passed to each task.
  *
  * This task itself might not be too useful in complex cases because of the argument sharing, though it
  * may be used as a template for creating task aggregates.
  */
class RunAllTasks[F[_]](
  F: QuasiIO[F],
  allTasks: Set[RoleTask[F]] @Id("all-custom-tasks"),
) extends RoleTask[F]
  with BundledTask {

  override def start(roleParameters: RawEntrypointParams, freeArgs: Vector[String]): F[Unit] = {
    F.traverse_(allTasks)(t => t.start(roleParameters, freeArgs))
  }
}

object RunAllTasks extends RoleDescriptor {
  override final val id = "all-tasks"

  override def parserSchema: RoleParserSchema = {
    RoleParserSchema(id, ParserDef.Empty, Some("run all available tasks (but not bundled DIStage tasks), reuse arguments"), None, freeArgsAllowed = true)
  }

}
