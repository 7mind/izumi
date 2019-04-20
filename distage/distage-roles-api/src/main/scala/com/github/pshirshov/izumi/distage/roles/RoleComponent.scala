package com.github.pshirshov.izumi.distage.roles

@deprecated("Migrate to DIResource", "2019-04-19")
trait RoleComponent {
  /** Should be idempotent, i.e. calling [[start()]] twice should NOT spawn a second component, interfere with the current instance or throw exceptions */
  def start(): Unit

  /** Should be idempotent, i.e. calling [[stop()]] twice should NOT throw exceptions */
  def stop(): Unit = {}
}


@deprecated("Migrate to new mechanism", "2019-04-19")
trait RoleService extends RoleComponent

@deprecated("Migrate to new mechanism", "2019-04-19")
trait RoleTask {
  this: RoleService =>
}


