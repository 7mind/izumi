package com.github.pshirshov.izumi.distage.roles

import com.github.pshirshov.izumi.distage.model.definition.DIResource
import com.github.pshirshov.izumi.distage.roles.cli.Parameters

@deprecated("Migrate to new mechanism", "2019-04-19")
trait RoleService extends RoleComponent

trait RoleService2[F[_]] {
  def start(roleParameters: Parameters, freeArgs: Vector[String]): DIResource[F, Unit]
}
