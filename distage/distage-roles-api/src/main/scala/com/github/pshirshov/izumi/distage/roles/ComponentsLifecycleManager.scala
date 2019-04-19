package com.github.pshirshov.izumi.distage.roles

@deprecated("Migrate to DIResource", "2019-04-19")
trait ComponentsLifecycleManager {
  def componentsNumber: Int

  def startComponents(): Unit

  def stopComponents(): Set[RoleComponent]
}
