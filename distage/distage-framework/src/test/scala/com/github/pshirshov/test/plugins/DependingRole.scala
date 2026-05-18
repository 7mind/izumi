package com.github.pshirshov.test.plugins

import izumi.functional.bio.Applicative2
import izumi.distage.roles.model.{RoleDescriptor, RoleTask}
import izumi.fundamentals.platform.cli.model.EntrypointArgs

class DependingRole[F[+_, +_]](
  val string: String
)(implicit F: Applicative2[F]
) extends RoleTask[F] {
  override def start(roleParameters: EntrypointArgs): F[Throwable, Unit] = F.unit
}

object DependingRole extends RoleDescriptor {
  final val id = "dependingrole"
}
