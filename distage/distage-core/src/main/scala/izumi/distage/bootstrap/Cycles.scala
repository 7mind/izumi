package izumi.distage.bootstrap

import izumi.distage.model.definition.Axis

object Cycles extends Axis {
  override def name: String = "cycles"

  case object Proxy extends AxisValueDef
  case object Byname extends AxisValueDef
  case object Disable extends AxisValueDef
}
