package com.github.pshirshov.izumi.distage.roles.roles

trait RoleAppComponent {
  def start(): Unit
  def stop(): Unit = {}
}
