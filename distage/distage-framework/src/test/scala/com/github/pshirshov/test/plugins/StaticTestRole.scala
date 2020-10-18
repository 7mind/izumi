package com.github.pshirshov.test.plugins

import izumi.distage.roles.model.{RoleDescriptor, RoleTask}
import izumi.fundamentals.platform.cli.model.raw.RawEntrypointParams
import izumi.fundamentals.platform.functional.Identity

class StaticTestRole(
  val testService: TestService
) extends RoleTask[Identity] {
  override def start(roleParameters: RawEntrypointParams, freeArgs: Vector[String]): Unit = ()
}

object StaticTestRole extends RoleDescriptor {
  final val id = "statictestrole"
}
