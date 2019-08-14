package izumi.distage.roles.services

import izumi.distage.model.definition.Binding
import izumi.distage.roles.model.meta.RolesInfo

trait RoleProvider[F[_]] {
  def getInfo(bindings: Seq[Binding]): RolesInfo
}
