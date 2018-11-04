package com.github.pshirshov.izumi.distage.roles.launcher

import com.github.pshirshov.izumi.distage.roles.roles.RoleComponent

sealed trait ComponentLifecycle

object ComponentLifecycle {

  final case class Starting(component: RoleComponent) extends ComponentLifecycle

  final case class Started(component: RoleComponent) extends ComponentLifecycle

}
