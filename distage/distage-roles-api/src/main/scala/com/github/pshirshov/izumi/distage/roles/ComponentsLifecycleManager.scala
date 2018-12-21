package com.github.pshirshov.izumi.distage.roles

trait ComponentsLifecycleManager {
  def componentsNumber: Int

  def startComponents(): Unit

  def stopComponents(): Set[RoleComponent]
}
