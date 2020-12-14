package izumi.distage.framework.services

import izumi.distage.framework.model.ActivationInfo
import izumi.distage.model.definition.{Axis, BindingTag, ModuleBase}
import izumi.distage.roles.model.exceptions.DIAppBootstrapException
import izumi.fundamentals.platform.strings.IzString._

trait ActivationChoicesExtractor {
  def findAvailableChoices(module: ModuleBase): ActivationInfo
}

object ActivationChoicesExtractor {

  class Impl extends ActivationChoicesExtractor {
    def findAvailableChoices(module: ModuleBase): ActivationInfo = {
      findAvailableChoicesDetailed(module) match {
        case Left(badAxis) =>
          val conflicts = badAxis.map {
            case (name, axes) =>
              s"$name: ${axes.map(a => a.getClass -> a.hashCode()).niceList().shift(2)}"
          }
          throw new DIAppBootstrapException(s"Conflicting axis names: ${conflicts.niceList()}")

        case Right(value) => value
      }
    }

    /** @return Either conflicting axis names or a set of axis choices present in the bindings */
    def findAvailableChoicesDetailed(module: ModuleBase): Either[Map[String, Set[Axis]], ActivationInfo] = {
      val allChoices = module.bindings.flatMap(_.tags).collect { case BindingTag.AxisTag(axisValue) => axisValue }
      val allAxis = allChoices.map(_.axis).groupBy(_.name)
      val badAxis = allAxis.filter(_._2.size > 1)
      if (badAxis.isEmpty) Right(ActivationInfo(allChoices.groupBy(_.axis))) else Left(badAxis)
    }
  }

}