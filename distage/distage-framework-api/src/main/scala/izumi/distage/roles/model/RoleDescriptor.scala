package izumi.distage.roles.model

import izumi.distage.roles.model.RoleDescriptor.GetRoleDescriptor
import izumi.fundamentals.platform.cli.model.schema.{ParserDef, RoleParserSchema}
import izumi.fundamentals.platform.resources.{IzArtifact, IzArtifactMaterializer}

import scala.annotation.implicitNotFound

trait RoleDescriptorBase {
  /** Name of the role, to be used on the command-line to launch it */
  def id: String

  def artifact: Option[IzArtifact]
  def parserSchema: RoleParserSchema
}

abstract class RoleDescriptor(implicit currentArtifact: IzArtifactMaterializer) extends RoleDescriptorBase {
  def artifact: Option[IzArtifact] = Some(currentArtifact.get)
  def parserSchema: RoleParserSchema = RoleParserSchema(id, ParserDef.Empty, None, None, freeArgsAllowed = false)

  implicit final def getSelfDescriptor: GetRoleDescriptor[Nothing] = GetRoleDescriptor(this)
}

object RoleDescriptor {
  @inline def apply[A](implicit getRoleDescriptor: GetRoleDescriptor[A]): RoleDescriptor = getRoleDescriptor.roleDescriptor

  @implicitNotFound(
    "Couldn't find a companion object `RoleDescriptor` for role ${A}, ${A} must have a _companion object_ that extends `izumi.distage.roles.model.RoleDescriptor` to be declared a role"
  )
  final case class GetRoleDescriptor[+A](roleDescriptor: RoleDescriptor) extends AnyVal
}
