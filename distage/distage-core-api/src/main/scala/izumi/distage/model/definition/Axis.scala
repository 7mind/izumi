package izumi.distage.model.definition

import izumi.distage.model.definition.Axis.AxisValue
import izumi.distage.model.planning.AxisPoint

trait Axis { self =>
  def name: String = getClass.getName.toLowerCase.split('.').last.split('$').last
  override final def toString: String = s"$name"

  abstract class AxisValueDef extends AxisValue {
    override val axis: Axis = self
  }
}

object Axis {

  trait AxisValue {
    def axis: Axis
    def value: String = getClass.getName.toLowerCase.split('.').last.split('$').last

    final def toAxisPoint: AxisPoint = AxisPoint(axis.name, value)

    override final def toString: String = s"$axis:$value"

    @deprecated("Renamed to `value`", "1.0")
    final def id: String = value
  }

}
