package com.github.pshirshov.izumi.distage.roles.model

import com.github.pshirshov.izumi.distage.model.definition.Axis.AxisMember
import com.github.pshirshov.izumi.distage.model.definition.AxisBase

case class AppActivation(choices: Map[AxisBase, Set[AxisMember]], active: Map[AxisBase, AxisMember])

object AppActivation {
  def empty: AppActivation = AppActivation(Map.empty, Map.empty)
}
