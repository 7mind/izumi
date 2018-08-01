package com.github.pshirshov.izumi.distage.roles.launcher

import com.github.pshirshov.izumi.distage.model.definition.Binding

trait RoleProvider {
  def getInfo(bindings: Iterable[Binding]): RolesInfo
}
