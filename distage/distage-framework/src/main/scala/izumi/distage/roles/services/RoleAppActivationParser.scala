package izumi.distage.roles.services

import izumi.distage.framework.model.ActivationInfo
import izumi.distage.model.definition.Activation
import izumi.distage.roles.RoleAppLauncher.Options
import izumi.distage.roles.model.exceptions.DIAppBootstrapException
import izumi.fundamentals.platform.cli.model.raw.RawAppArgs
import izumi.fundamentals.platform.strings.IzString._
import izumi.logstage.api.IzLogger

class RoleAppActivationParser {
  def parseActivation(
                       logger: IzLogger,
                       parameters: RawAppArgs,
                       activationInfo: ActivationInfo,
                       defaultActivations: Activation,
                     ): Activation = {
    val uses = parameters.globalParameters.findValues(Options.use)

    def options: String = {
      activationInfo.availableChoices.map {
        case (axis, members) =>
          s"$axis:${members.niceList().shift(2)}"
      }.niceList()
    }

    val activeChoices = uses.map {
      rawValue =>
        val (axisName, choiceName) = rawValue.value.split2(':')
        activationInfo.availableChoices.find(_._1.name == axisName) match {
          case Some((base, members)) =>
            members.find(_.id == choiceName) match {
              case Some(member) =>
                base -> member
              case None =>
                logger.crit(s"Unknown choice: $choiceName")
                logger.crit(s"Available $options")
                throw new DIAppBootstrapException(s"Unknown choice: $choiceName")
            }

          case None =>
            logger.crit(s"Unknown axis: $axisName")
            logger.crit(s"Available $options")
            throw new DIAppBootstrapException(s"Unknown axis: $axisName")
        }
    }

    import izumi.fundamentals.collections.IzCollections._
    val badChoices = activeChoices.toMultimap.filter(_._2.size > 1)
    if (badChoices.nonEmpty) {
      val conflicts = badChoices
        .map {
          case (axis, axisValues) =>
            s"$axis: ${axisValues.mkString(", ")}"
        }.niceList()
      logger.crit(s"Conflicting choices, you can activate one choice on each axis $conflicts")
      throw new DIAppBootstrapException(s"Conflicting choices, you can activate one choice on each axis $conflicts")
    } else {
      Activation(defaultActivations.activeChoices ++ activeChoices)
    }
  }

}
