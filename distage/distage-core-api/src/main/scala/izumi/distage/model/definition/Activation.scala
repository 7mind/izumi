package izumi.distage.model.definition

import izumi.distage.model.definition.Axis.AxisValue

final case class Activation(activeChoices: Map[Axis, AxisValue]) extends AnyVal {
  @inline def ++(activation: Activation): Activation = Activation(activeChoices ++ activation.activeChoices)
  @inline def +(axisChoice: (Axis, AxisValue)): Activation = this ++ Activation(axisChoice._1 -> axisChoice._2)
}

object Activation {
  def apply(activeChoices: (Axis, AxisValue)*): Activation = Activation(Map(activeChoices: _*))
  def apply(): Activation = empty
  val empty: Activation = new Activation(Map.empty)
}
