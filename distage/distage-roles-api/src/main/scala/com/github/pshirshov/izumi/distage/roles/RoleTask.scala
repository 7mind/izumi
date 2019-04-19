package com.github.pshirshov.izumi.distage.roles

import com.github.pshirshov.izumi.distage.roles.cli.Parameters

@deprecated("Migrate to new mechanism", "2019-04-19")
trait RoleTask {
  this: RoleService =>
}


/**
  * Single-shot task, shouldn't block forever
  */
trait RoleTask2[F[_]] {
  def start(roleParameters: Parameters, freeArgs: Vector[String]): F[Unit]
}
