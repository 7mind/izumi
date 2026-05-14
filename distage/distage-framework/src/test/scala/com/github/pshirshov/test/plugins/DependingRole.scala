package com.github.pshirshov.test.plugins

import izumi.functional.bio.Applicative1
import izumi.distage.roles.model.{RoleDescriptor, RoleTask}
import izumi.fundamentals.platform.cli.model.EntrypointArgs

class DependingRole[F[_]](
  val string: String
)(implicit F: Applicative1[F]
) extends RoleTask[F] {
  override def start(roleParameters: EntrypointArgs): F[Unit] = F.unit
}

object DependingRole extends RoleDescriptor {
  final val id = "dependingrole"
}
