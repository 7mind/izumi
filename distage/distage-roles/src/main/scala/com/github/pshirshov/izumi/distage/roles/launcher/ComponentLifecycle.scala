package com.github.pshirshov.izumi.distage.roles.launcher

import com.github.pshirshov.izumi.distage.roles.roles.RoleComponent

sealed trait ComponentLifecycle

object ComponentLifecycle {

  final case class Starting(component: RoleComponent) extends ComponentLifecycle {
    override def toString: String = s"$component [starting]"
  }

  final case class Started(component: RoleComponent) extends ComponentLifecycle {
    override def toString: String = component.toString
  }

}
