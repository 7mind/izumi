package com.github.pshirshov.test.plugins

import izumi.distage.model.Planner
import izumi.distage.model.definition.{Id, Module}
import izumi.functional.bio.{Applicative2, Bifunctorized}
import izumi.distage.model.recursive.LocatorRef
import izumi.distage.roles.model.{RoleDescriptor, RoleTask}
import izumi.functional.bio.Clock2
import izumi.fundamentals.platform.cli.model.EntrypointArgs
import logstage.LogIO

class StaticTestRole[F[+_, +_]](
  val testService: TestService,
  val defaultModule: Module @Id("defaultModule"),
  val locatorRef: LocatorRef,
  val planner: Planner,
  val clock: Clock2[F],
  val clockId: Clock2[Bifunctorized.IdentityBifunctorized],
  val log: LogIO[F[Nothing, _]],
)(implicit F: Applicative2[F]
) extends RoleTask[F] {
  override def start(roleParameters: EntrypointArgs): F[Throwable, Unit] = F.unit
}

object StaticTestRole extends RoleDescriptor {
  final val id = "statictestrole"
}
