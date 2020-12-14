package izumi.distage.framework.model

import izumi.distage.model.definition.Axis
import izumi.distage.model.definition.Axis.AxisChoice

import scala.annotation.nowarn

@nowarn("msg=Unused import")
final case class ActivationInfo(availableChoices: Map[Axis, Set[AxisChoice]]) extends AnyVal {
  import scala.collection.compat._

  override def toString: String = {
    s"{available.activations: $formattedChoices }"
  }

  def narrow(keys: Set[Axis]): ActivationInfo = ActivationInfo(availableChoices.view.filterKeys(keys).toMap)

  def formattedChoices: String = {
    availableChoices.map { case (k, v) => s"${k.name}:${v.map(_.value).mkString("(", "|", ")")}" }.mkString(" ")
  }
}
