package com.github.pshirshov.test.plugins

import izumi.functional.quasi.QuasiApplicative
import izumi.distage.roles.model.{RoleDescriptor, RoleTask}
import izumi.fundamentals.platform.cli.model.EntrypointArgs

class DependingRole[F[_]](
  val string: String
)(implicit F: QuasiApplicative[F]
) extends RoleTask[F] {
  override def start(roleParameters: EntrypointArgs): F[Unit] = F.unit
}

object DependingRole extends RoleDescriptor {
  final val id = "dependingrole"
}
