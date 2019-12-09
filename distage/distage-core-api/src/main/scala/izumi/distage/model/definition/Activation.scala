package izumi.distage.model.definition

import izumi.distage.model.definition.Axis.AxisValue

final case class Activation(activeChoices: Map[Axis, AxisValue]) extends AnyVal {
  def ++(activation: Activation) = Activation(activeChoices ++ activation.activeChoices)
}

object Activation {
  def apply(activeChoices: (Axis, AxisValue)*): Activation = Activation(Map(activeChoices: _*))
  def empty: Activation = Activation()
}
