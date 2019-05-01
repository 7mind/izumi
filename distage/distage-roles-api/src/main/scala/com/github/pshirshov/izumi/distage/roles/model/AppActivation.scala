package com.github.pshirshov.izumi.distage.roles.model

import com.github.pshirshov.izumi.distage.model.definition.Axis.AxisValue
import com.github.pshirshov.izumi.distage.model.definition.AxisBase

case class AppActivation(choices: Map[AxisBase, Set[AxisValue]], active: Map[AxisBase, AxisValue])

object AppActivation {
  def empty: AppActivation = AppActivation(Map.empty, Map.empty)
}
