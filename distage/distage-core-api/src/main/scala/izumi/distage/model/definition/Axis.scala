package izumi.distage.model.definition

import izumi.distage.model.definition.Axis.AxisValue

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
    def id: String = getClass.getName.toLowerCase.split('.').last.split('$').last

    override final def toString: String = s"$axis:$id"
  }
}


