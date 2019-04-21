package com.github.pshirshov.izumi.distage.roles

import com.github.pshirshov.izumi.distage.model.definition.DIResource
import com.github.pshirshov.izumi.distage.roles.cli.Parameters


sealed trait AbstractRoleF[F[_]]

trait RoleService2[F[_]] extends AbstractRoleF[F] {
  /**
    * Application startup wouldn't progress until this method finishes.
    * Resource initialization must be finite
    * You may run a separate thread, a fiber, etc during resource initialization
    * All the shutdown logic has to be implemented in resource finalizer
    */
  def start(roleParameters: Parameters, freeArgs: Vector[String]): DIResource[F, Unit]
}

/**
  * Single-shot task, shouldn't block forever
  */
trait RoleTask2[F[_]] extends AbstractRoleF[F] {
  /**
    * Application startup wouldn't progress until this method finishes
    */
  def start(roleParameters: Parameters, freeArgs: Vector[String]): F[Unit]
}
