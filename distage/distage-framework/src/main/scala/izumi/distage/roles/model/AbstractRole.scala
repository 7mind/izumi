package izumi.distage.roles.model

import izumi.distage.model.definition.DIResource.DIResourceBase
import izumi.fundamentals.platform.cli.model.raw.RawEntrypointParams

sealed trait AbstractRole[+F[_]]

trait RoleService[+F[_]] extends AbstractRole[F] {
  /**
    * Application startup wouldn't progress until this method finishes.
    * Resource initialization must be finite
    * You may run a separate thread, a fiber, etc during resource initialization
    * All the shutdown logic has to be implemented in the resource finalizer
    */
  def start(roleParameters: RawEntrypointParams, freeArgs: Vector[String]): DIResourceBase[F, Unit]
}

/**
  * Single-shot task, shouldn't block forever
  */
trait RoleTask[+F[_]] extends AbstractRole[F] {
  /**
    * Application startup wouldn't progress until this method finishes
    */
  def start(roleParameters: RawEntrypointParams, freeArgs: Vector[String]): F[Unit]
}
