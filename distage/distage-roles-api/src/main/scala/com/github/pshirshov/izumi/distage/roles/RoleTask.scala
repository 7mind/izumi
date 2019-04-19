package com.github.pshirshov.izumi.distage.roles

trait RoleTask {
  this: RoleService =>
}


trait RoleTask2 {
  def start(parameters: RoleP)
}
