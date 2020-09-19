package izumi.distage.roles.model

import izumi.distage.roles.model.RoleDescriptor.GetRoleDescriptor
import izumi.fundamentals.platform.cli.model.schema.{ParserDef, RoleParserSchema}
import izumi.fundamentals.platform.resources.{IzArtifact, IzArtifactMaterializer}

trait RoleDescriptorBase {
  def id: String

  def artifact: Option[IzArtifact]

  def parserSchema: RoleParserSchema
}

abstract class RoleDescriptor()(implicit currentArtifact: IzArtifactMaterializer) extends RoleDescriptorBase {
  def artifact: Option[IzArtifact] = Some(currentArtifact.get)

  def parserSchema: RoleParserSchema = RoleParserSchema(id, ParserDef.Empty, None, None, freeArgsAllowed = false)

  implicit final def getSelfDescriptor: GetRoleDescriptor[Nothing] = GetRoleDescriptor(this)
}

object RoleDescriptor {
  @inline def apply[A](implicit getRoleDescriptor: GetRoleDescriptor[A]): RoleDescriptor = getRoleDescriptor.roleDescriptor

  final case class GetRoleDescriptor[+A](roleDescriptor: RoleDescriptor) extends AnyVal
}
