package izumi.distage.roles.model

import distage.ModuleBase
import izumi.distage.model.definition.Axis.AxisValue
import izumi.distage.model.definition.{Axis, BindingTag}
import izumi.fundamentals.platform.strings.IzString._
import izumi.logstage.api.IzLogger

final case class ActivationInfo(availableChoices: Map[Axis, Set[AxisValue]]) extends AnyVal

object ActivationInfo {
  def findAvailableChoices(logger: IzLogger, module: ModuleBase): ActivationInfo = {
    findAvailableChoices(module) match {
      case Left(badAxis) =>
        val conflicts = badAxis.map {
          case (name, axes) =>
            s"$name: ${axes.niceList().shift(2)}"
        }
        logger.crit(s"Conflicting axis ${conflicts.niceList() -> "names"}")
        throw new DIAppBootstrapException(s"Conflicting axis: $conflicts")

      case Right(value) => value
    }
  }

  /** @return Either conflicting axis names or a set of axis choices present in the bindings */
  def findAvailableChoices(module: ModuleBase): Either[Map[String, Set[Axis]], ActivationInfo] = {
    val allChoices = module.bindings.flatMap(_.tags).collect { case BindingTag.AxisTag(choice) => choice }
    val allAxis = allChoices.map(_.axis).groupBy(_.name)
    val badAxis = allAxis.filter(_._2.size > 1)
    if (badAxis.isEmpty) Right(ActivationInfo(allChoices.groupBy(_.axis))) else Left(badAxis)
  }
}
