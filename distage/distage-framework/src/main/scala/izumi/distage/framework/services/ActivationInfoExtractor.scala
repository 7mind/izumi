package izumi.distage.framework.services

import distage.ModuleBase
import izumi.distage.framework.model.ActivationInfo
import izumi.distage.model.definition.{Axis, BindingTag}
import izumi.distage.roles.model.exceptions.DIAppBootstrapException
import izumi.fundamentals.platform.strings.IzString._
import izumi.logstage.api.IzLogger

object ActivationInfoExtractor {
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
