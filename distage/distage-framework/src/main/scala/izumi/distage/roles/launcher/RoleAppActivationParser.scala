package izumi.distage.roles.launcher

import izumi.distage.framework.model.ActivationInfo
import izumi.distage.model.definition.{Activation, Axis, Id}
import izumi.distage.model.planning.AxisPoint
import izumi.distage.roles.DebugProperties
import izumi.distage.roles.model.exceptions.DIAppBootstrapException
import izumi.fundamentals.platform.strings.IzString._
import izumi.logstage.api.IzLogger

trait RoleAppActivationParser {
  def parseActivation(rawActivations: Iterable[AxisPoint], activationInfo: ActivationInfo): Activation
}

object RoleAppActivationParser {
  private[this] final val sysPropIgnoreUnknownActivations = DebugProperties.`izumi.distage.roles.activation.ignore-unknown`.boolValue(false)

  class Impl(
    logger: IzLogger,
    ignoreUnknownActivations: Boolean @Id("distage.roles.activation.ignore-unknown"),
  ) extends RoleAppActivationParser {

    override def parseActivation(rawActivations: Iterable[AxisPoint], activationInfo: ActivationInfo): Activation = {
      val usedChoices = rawActivations.flatMap {
        case AxisPoint(axisName, choiceName) =>
          validateAxisChoice(activationInfo)(axisName, choiceName)
      }
      validateAllChoices(usedChoices)

      Activation(usedChoices.toMap)
    }

    protected def validateAxisChoice(activationInfo: ActivationInfo)(axisName: String, choiceName: String): Option[(Axis, Axis.AxisValue)] = {
      def options: String = {
        activationInfo
          .availableChoices.map {
            case (axis, members) =>
              s"$axis:${members.niceList().shift(2)}"
          }.niceList()
      }

      activationInfo.availableChoices.find(_._1.name == axisName) match {
        case Some((base, members)) =>
          members.find(_.value == choiceName) match {
            case Some(member) =>
              Some(base -> member)
            case None =>
              logger.crit(s"Unknown choice: $choiceName")
              logger.crit(s"Available $options")
              if (ignoreUnknownActivations || sysPropIgnoreUnknownActivations) {
                None
              } else {
                throw new DIAppBootstrapException(s"Unknown choice: $choiceName")
              }
          }

        case None =>
          logger.crit(s"Unknown axis: $axisName")
          logger.crit(s"Available $options")
          if (ignoreUnknownActivations || sysPropIgnoreUnknownActivations) {
            None
          } else {
            throw new DIAppBootstrapException(s"Unknown axis: $axisName")
          }
      }
    }

    protected def validateAllChoices(choices: Iterable[(Axis, Axis.AxisValue)]): Unit = {
      import izumi.fundamentals.collections.IzCollections._

      val badChoices = choices.toMultimap.filter(_._2.size > 1)
      if (badChoices.nonEmpty) {
        val conflicts = badChoices.map { case (axis, axisValues) => s"$axis: ${axisValues.mkString(", ")}" }.niceList()

        logger.crit(s"Conflicting choices, you can activate one choice on each axis $conflicts")
        throw new DIAppBootstrapException(s"Conflicting choices, you can activate one choice on each axis $conflicts")
      }
    }

  }

}
