package com.github.pshirshov.izumi.distage.roles.launcher

import com.github.pshirshov.izumi.distage.model.definition.Binding
import com.github.pshirshov.izumi.distage.roles.RolesInfo

trait RoleProvider[F[_]] {
  def getInfo(bindings: Iterable[Binding]): RolesInfo
}
