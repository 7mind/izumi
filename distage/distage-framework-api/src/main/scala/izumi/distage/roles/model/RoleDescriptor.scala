package izumi.distage.roles.model

import izumi.distage.roles.model.RoleDescriptor.GetRoleDescriptor
import izumi.fundamentals.platform.cli.model.schema.{ParserDef, RoleParserSchema}

trait RoleDescriptor {
  def id: String
  def parserSchema: RoleParserSchema = RoleParserSchema(id, ParserDef.Empty, None, None, freeArgsAllowed = false)

  implicit final def getSelfDescriptor: GetRoleDescriptor[Nothing] = GetRoleDescriptor(this)
}

object RoleDescriptor {
  @inline def apply[A](implicit getRoleDescriptor: GetRoleDescriptor[A]): RoleDescriptor = getRoleDescriptor.roleDescriptor

  final case class GetRoleDescriptor[+A](roleDescriptor: RoleDescriptor) extends AnyVal
}
