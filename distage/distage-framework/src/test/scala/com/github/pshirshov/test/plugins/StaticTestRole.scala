package com.github.pshirshov.test.plugins

import izumi.distage.model.Planner
import izumi.distage.model.definition.{Id, Module}
import izumi.distage.model.effect.QuasiApplicative
import izumi.distage.model.recursive.LocatorRef
import izumi.distage.roles.model.{RoleDescriptor, RoleTask}
import izumi.fundamentals.platform.cli.model.raw.RawEntrypointParams

class StaticTestRole[F[_]](
  val testService: TestService,
  val defaultModule: Module @Id("defaultModule"),
  val locatorRef: LocatorRef,
  val planner: Planner,
)(implicit F: QuasiApplicative[F]
) extends RoleTask[F] {
  override def start(roleParameters: RawEntrypointParams, freeArgs: Vector[String]): F[Unit] = F.unit
}

object StaticTestRole extends RoleDescriptor {
  final val id = "statictestrole"
}
