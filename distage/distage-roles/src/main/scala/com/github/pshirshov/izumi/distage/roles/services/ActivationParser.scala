package com.github.pshirshov.izumi.distage.roles.services

import com.github.pshirshov.izumi.distage.model.definition.Axis.AxisMember
import com.github.pshirshov.izumi.distage.model.definition.{AxisBase, BindingTag}
import com.github.pshirshov.izumi.distage.roles.RoleAppLauncher.Options
import com.github.pshirshov.izumi.distage.roles.model.{AppActivation, DiAppBootstrapException}
import com.github.pshirshov.izumi.fundamentals.platform.cli.model.raw.RawAppArgs
import com.github.pshirshov.izumi.logstage.api.IzLogger
import distage.ModuleBase
import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._

class ActivationParser {
  def parseActivation(
                       logger: IzLogger,
                       parameters: RawAppArgs,
                       defApp: ModuleBase,
                       defaultActivations: Map[AxisBase, AxisMember],
                       requiredActivations: Map[AxisBase, AxisMember],
                     ): AppActivation = {
    val uses = Options.use.findValues(parameters.globalParameters)
    val availableUses: Map[AxisBase, Set[AxisMember]] = ActivationParser.findAvailableChoices(logger, defApp)


    def options: String = availableUses
      .map {
        case (axis, members) =>
          s"$axis:${members.niceList().shift(2)}"
      }
      .niceList()

    val activeChoices = uses
      .map {
        c =>
          val (axisName, choiceName) = c.value.split2(':')
          availableUses.find(_._1.name == axisName) match {
            case Some((base, members)) =>
              members.find(_.id == choiceName) match {
                case Some(member) =>
                  base -> member
                case None =>
                  logger.crit(s"Unknown choice: $choiceName")
                  logger.crit(s"Available $options")
                  throw new DiAppBootstrapException(s"Unknown choice: $choiceName")
              }

            case None =>
              logger.crit(s"Unknown axis: $axisName")
              logger.crit(s"Available $options")
              throw new DiAppBootstrapException(s"Unknown axis: $axisName")
          }
      }

    import com.github.pshirshov.izumi.fundamentals.collections.IzCollections._
    val badChoices = activeChoices.toMultimap.filter(_._2.size > 1)
    if (badChoices.nonEmpty) {
      val conflicts = badChoices
        .map {
          case (name, c) =>
            s"$name: ${c.mkString(", ")}"
        }
        .niceList()
      logger.crit(s"Conflicting choices, you can activate one choice on each axis $conflicts")
      throw new DiAppBootstrapException(s"Conflicting choices, you can activate one choice on each axis $conflicts")
    }

    AppActivation(availableUses, defaultActivations ++ activeChoices ++ requiredActivations)
  }


}

object ActivationParser {
  def findAvailableChoices(logger: IzLogger, defApp: ModuleBase): Map[AxisBase, Set[AxisMember]] = {
    val allChoices = defApp.bindings.flatMap(_.tags).collect({ case BindingTag.AxisTag(choice) => choice })
    val allAxis = allChoices.map(_.axis).groupBy(_.name)
    val badAxis = allAxis.filter(_._2.size > 1)
    if (badAxis.nonEmpty) {
      val conflicts = badAxis.map {
        case (name, value) =>
          s"$name: ${value.niceList().shift(2)}"
      }
      logger.crit(s"Conflicting axis ${conflicts.niceList() -> "names"}")
      throw new DiAppBootstrapException(s"Conflicting axis: $conflicts")
    }
    val availableUses = allChoices.groupBy(_.axis)
    availableUses
  }
}
