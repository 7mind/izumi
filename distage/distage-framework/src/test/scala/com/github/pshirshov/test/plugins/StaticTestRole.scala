package com.github.pshirshov.test.plugins

import izumi.distage.model.Planner
import izumi.distage.model.definition.{Id, Module}
import izumi.functional.bio.Applicative1
import izumi.distage.model.recursive.LocatorRef
import izumi.distage.roles.model.{RoleDescriptor, RoleTask}
import izumi.functional.bio.Clock1
import izumi.fundamentals.platform.cli.model.EntrypointArgs
import izumi.fundamentals.platform.functional.Identity
import logstage.LogIO

class StaticTestRole[F[_]](
  val testService: TestService,
  val defaultModule: Module @Id("defaultModule"),
  val locatorRef: LocatorRef,
  val planner: Planner,
  val clock: Clock1[F],
  val clockId: Clock1[Identity],
  val log: LogIO[F],
)(implicit F: Applicative1[F]
) extends RoleTask[F] {
  override def start(roleParameters: EntrypointArgs): F[Unit] = F.unit
}

object StaticTestRole extends RoleDescriptor {
  final val id = "statictestrole"
}
