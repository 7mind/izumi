package izumi.distage.model.definition

import izumi.distage.model.definition.Axis.AxisValue
import izumi.fundamentals.platform.strings.IzString.toRichString

trait Axis { self =>
  def name: String = getClass.getName.toLowerCase.split('.').last.split('$').last
  override final def toString: String = s"$name"

  abstract class AxisValueDef extends AxisValue {
    override val axis: Axis = self
  }
}

object Axis {

  final case class AxisPoint(axis: String, value: String) {
    override def toString: String = s"$axis:$value"
  }

  trait AxisValue {
    def axis: Axis
    def id: String = getClass.getName.toLowerCase.split('.').last.split('$').last

    final def toAxisPoint: AxisPoint = AxisPoint(axis.name, id)

    override final def toString: String = s"$axis:$id"
  }
  object AxisValue {
    def splitAxisValue(s: String): (String, String) = {
      s.split2(':')
    }
  }
}
