package izumi.distage.roles.bundled

import distage.Id
import izumi.distage.model.definition.Lifecycle
import izumi.distage.roles.model.{RoleDescriptor, RoleService}
import izumi.functional.quasi.QuasiIO
import izumi.fundamentals.platform.cli.model.raw.RawEntrypointParams
import izumi.fundamentals.platform.cli.model.schema.*

/**
  * This service runs all the non-bundled distage services with its arguments passed to each task.
  *
  * This service itself might not be too useful in complex cases because of the argument sharing, though it
  * may be used as a template for creating service aggregates.
  */
class RunAllRoles[F[_]](
  allTasks: Set[RoleService[F]] @Id("all-custom-roles")
)(implicit F: QuasiIO[F]
) extends RoleService[F]
  with BundledTask {
  override def start(roleParameters: RawEntrypointParams, freeArgs: Vector[String]): Lifecycle[F, Unit] = {
    Lifecycle.traverse_(allTasks)(_.start(roleParameters, freeArgs))
  }
}

object RunAllRoles extends RoleDescriptor {
  override final val id = "all-roles"

  override def parserSchema: RoleParserSchema = {
    RoleParserSchema(id, ParserDef.Empty, Some("run all available services (but not bundled DIStage services), reuse arguments"), None, freeArgsAllowed = true)
  }

}
