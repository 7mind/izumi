package izumi.distage.bootstrap

import izumi.distage.model.definition.Axis

object Cycles extends Axis {
  override def name: String = "cycles"

  /** Enable cglib proxies, but try to resolve cycles using by-name parameters if they can be used */
  case object Proxy extends AxisChoiceDef

  /** Disable cglib proxies, allow only by-name parameters to resolve cycles */
  case object Byname extends AxisChoiceDef

  /** Disable all cycle resolution, immediately throw when circular dependencies are found, whether by-name or not */
  case object Disable extends AxisChoiceDef
}
