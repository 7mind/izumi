package com.github.pshirshov.izumi.distage.roles.model

import com.github.pshirshov.izumi.distage.model.definition.DIResource.DIResourceBase
import com.github.pshirshov.izumi.fundamentals.platform.cli.model.raw.RawEntrypointParams

sealed trait AbstractRoleF[+F[_]]

trait RoleService[+F[_]] extends AbstractRoleF[F] {
  /**
    * Application startup wouldn't progress until this method finishes.
    * Resource initialization must be finite
    * You may run a separate thread, a fiber, etc during resource initialization
    * All the shutdown logic has to be implemented in resource finalizer
    */
  def start(roleParameters: RawEntrypointParams, freeArgs: Vector[String]): DIResourceBase[F, Unit]
}

/**
  * Single-shot task, shouldn't block forever
  */
trait RoleTask[+F[_]] extends AbstractRoleF[F] {
  /**
    * Application startup wouldn't progress until this method finishes
    */
  def start(roleParameters: RawEntrypointParams, freeArgs: Vector[String]): F[Unit]
}
