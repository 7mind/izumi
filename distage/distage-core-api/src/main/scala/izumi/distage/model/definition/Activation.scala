package izumi.distage.model.definition

import izumi.distage.model.definition.Axis.AxisValue

final case class Activation(activeChoices: Map[Axis, AxisValue]) extends AnyVal {
  @inline def ++(activation: Activation): Activation = Activation(activeChoices ++ activation.activeChoices)
  @inline def +(axisChoice: (Axis, AxisValue)): Activation = Activation(activeChoices + axisChoice)

  /** `that` activation is larger than `this` activation if all axis choices in `this` are present in `that` */
  def <=(that: Activation): Boolean = activeChoices.toSet.subsetOf(that.activeChoices.toSet)
}

object Activation {
  def apply(activeChoices: (Axis, AxisValue)*): Activation = Activation(Map(activeChoices: _*))
  def apply(): Activation = empty
  def empty: Activation = new Activation(Map.empty)
}
