package izumi.distage.framework.model

import izumi.distage.model.definition.Axis
import izumi.distage.model.definition.Axis.AxisValue

final case class ActivationInfo(availableChoices: Map[Axis, Set[AxisValue]]) extends AnyVal
