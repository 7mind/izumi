package izumi.distage.model.definition

import izumi.distage.model.definition.Axis.AxisChoice
import izumi.distage.model.planning.AxisPoint

trait Axis { self =>
  def name: String = getClass.getName.toLowerCase.split('.').last.split('$').last
  override final def toString: String = s"$name"

  abstract class AxisChoiceDef extends AxisChoice {
    override val axis: Axis = self
  }

  @deprecated("Renamed to AxisChoiceDef", "1.0")
  final type AxisValueDef = AxisChoiceDef
}

object Axis {

  trait AxisChoice {
    def axis: Axis
    def value: String = getClass.getName.toLowerCase.split('.').last.split('$').last

    final def toAxisPoint: AxisPoint = AxisPoint(axis.name, value)

    override final def toString: String = s"$axis:$value"

    @deprecated("Renamed to `value`", "1.0")
    final def id: String = value
  }

  @deprecated("Renamed to AxisChoice", "1.0")
  type AxisValue = AxisChoice
}
