package com.github.pshirshov.izumi.distage.roles

trait RoleComponent {
  /** Should be idempotent, i.e. calling [[start()]] twice should NOT spawn a second component, interfere with the current instance or throw exceptions */
  def start(): Unit

  /** Should be idempotent, i.e. calling [[stop()]] twice should NOT throw exceptions */
  def stop(): Unit = {}
}
