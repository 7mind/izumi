package com.github.pshirshov.izumi.distage.roles.services

import com.github.pshirshov.izumi.distage.model.definition.Binding
import com.github.pshirshov.izumi.distage.roles.model.RolesInfo

trait RoleProvider[F[_]] {
  def getInfo(bindings: Iterable[Binding]): RolesInfo
}
