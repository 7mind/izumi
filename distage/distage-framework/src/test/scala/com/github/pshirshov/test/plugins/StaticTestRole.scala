package com.github.pshirshov.test.plugins

import izumi.distage.model.Planner
import izumi.distage.model.definition.{Id, Module}
import izumi.distage.model.effect.QuasiApplicative
import izumi.distage.model.recursive.LocatorRef
import izumi.distage.roles.model.{RoleDescriptor, RoleTask}
import izumi.functional.mono.Clock
import izumi.fundamentals.platform.cli.model.raw.RawEntrypointParams
import izumi.fundamentals.platform.functional.Identity
import logstage.LogIO

class StaticTestRole[F[_]](
  val testService: TestService,
  val defaultModule: Module @Id("defaultModule"),
  val locatorRef: LocatorRef,
  val planner: Planner,
  val clock: Clock[F],
  val clockId: Clock[Identity],
  val log: LogIO[F],
)(implicit F: QuasiApplicative[F]
) extends RoleTask[F] {
  override def start(roleParameters: RawEntrypointParams, freeArgs: Vector[String]): F[Unit] = F.unit
}

object StaticTestRole extends RoleDescriptor {
  final val id = "statictestrole"
}
