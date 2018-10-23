package com.github.pshirshov.izumi.distage.roles.roles

trait RoleComponent {
  /** Should be idempotent, i.e. [[start()]] on a running component shouldn't spawn a second component, interfere with the current instance or throw */
  def start(): Unit

  /** Should be idempotent, i.e. [[stop()]] on an already stopped component shouldn't throw */
  def stop(): Unit = {}
}
