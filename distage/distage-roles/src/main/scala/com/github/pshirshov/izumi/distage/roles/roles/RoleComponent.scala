package com.github.pshirshov.izumi.distage.roles.roles

trait RoleComponent {
  def start(): Unit
  def stop(): Unit = {}
}
