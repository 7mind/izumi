package izumi.distage.planning.solver

import izumi.distage.model.definition.Axis.AxisPoint

final case class ActivationChoices(activationChoices: Map[String, AxisPoint]) extends AnyVal {
  def allValid(a: Set[AxisPoint]): Boolean = a.forall(validChoice)

  def allConfigured(a: Set[AxisPoint]): Boolean = a.forall(axisIsConfigured)

  def findUnconfigured(a: Set[AxisPoint]): Set[String] = {
    a.collect { case a if !activationChoices.contains(a.axis) => a.axis }
  }

  private[this] def validChoice(a: AxisPoint): Boolean = {
    // forall, as in, AxisPoint without an explicit choice is allowed through and should raise conflict in later stages
    // if there's another AxisPoint for the same axis (revisit this though)
    activationChoices.get(a.axis).forall(_ == a)
  }

  private[this] def axisIsConfigured(a: AxisPoint): Boolean = {
    // forall, as in, AxisPoint without an explicit choice is allowed through and should raise conflict in later stages
    // if there's another AxisPoint for the same axis (revisit this though)
    activationChoices.contains(a.axis) //  .get(a.axis).contains(a)
  }
}

object ActivationChoices {
  def apply(activations: Set[AxisPoint]): ActivationChoices = {
    new ActivationChoices(activations.map(a => (a.axis, a)).toMap)
  }
}
