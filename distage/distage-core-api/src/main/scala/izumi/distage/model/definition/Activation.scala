package izumi.distage.model.definition

import izumi.distage.model.definition.Axis.AxisValue

/**
  * Selection of active choices among those available in an Activation Axis
  *
  * {{{
  *   import distage.{Activation, Repo, Mode}
  *
  *   Activation(
  *     Repo -> Repo.Prod,
  *     Mode -> Mode.Test,
  *   )
  * }}}
  *
  * @see [[https://izumi.7mind.io/distage/basics#activation-axis Activation Axis]]
  */
final case class Activation(activeChoices: Map[Axis, AxisValue]) extends AnyVal {
  @inline def ++(activation: Activation): Activation = Activation(activeChoices ++ activation.activeChoices)
  @inline def +(axisChoice: (Axis, AxisValue)): Activation = Activation(activeChoices + axisChoice)
  @inline def +(axisValue: AxisValue): Activation = Activation(activeChoices + (axisValue.axis -> axisValue))

  /** `that` activation subsumes `this` activation if all axis choices in `this` are present in `that` */
  def <=(that: Activation): Boolean = activeChoices.toSet.subsetOf(that.activeChoices.toSet)
  @inline def subsetOf(that: Activation): Boolean = this <= that
}

object Activation {
  def apply(activeChoices: (Axis, AxisValue)*): Activation = Activation(activeChoices.toMap)
  def apply(activeChoices: AxisValue*)(implicit d: DummyImplicit): Activation = Activation(activeChoices.iterator.map(v => v.axis -> v).toMap)
  def apply(): Activation = empty
  def empty: Activation = Activation(Map.empty[Axis, AxisValue])
}
