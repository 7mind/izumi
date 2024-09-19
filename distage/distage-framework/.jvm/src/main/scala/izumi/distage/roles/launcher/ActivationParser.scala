package izumi.distage.roles.launcher

import distage.config.AppConfig
import izumi.distage.framework.model.ActivationInfo
import izumi.distage.model.definition.{Activation, Id}
import izumi.distage.model.planning.AxisPoint
import izumi.distage.roles.{DebugProperties, RoleAppMain}
import izumi.fundamentals.platform.cli.model.raw.RawAppArgs
import izumi.logstage.api.IzLogger

/**
  * Note, besides replacing this class, activation parsing strategy can also be changed by using bootstrap modules or plugins
  * and adding an override for `make[Activation].named("roleapp")` to [[izumi.distage.roles.RoleAppMain#roleAppBootOverrides]]
  */
trait ActivationParser extends AbstractActivationParser

object ActivationParser {

  class Impl(
    parser: RoleAppActivationParser,
    parameters: RawAppArgs,
    activationInfo: ActivationInfo,
    defaultActivations: Activation @Id("default"),
    additionalActivations: Activation @Id("additional"),
    logger: IzLogger,
    warnUnsetActivations: Boolean @Id("distage.roles.activation.warn-unset"),
  ) extends ActivationParser {

    def parseActivation(config: AppConfig): Activation = {
      val cmdChoices = parameters.globalParameters.findValues(RoleAppMain.Options.use).map(AxisPoint `parseAxisPoint` _.value)
      val cmdActivations = parser.parseActivation(cmdChoices, activationInfo)

      // println(s"PARSEACT: ${config.config}")
      val configChoices = if (config.config.hasPath(configActivationSection)) {
        ActivationConfig.diConfigReader.decodeConfig(configActivationSection)(config.config).activation.map(AxisPoint(_))
      } else Iterable.empty
      val configActivations = parser.parseActivation(configChoices, activationInfo)

      val resultActivation = defaultActivations ++
        additionalActivations ++
        configActivations ++
        cmdActivations // commandline choices override values in config

      val unsetActivations = activationInfo.availableChoices.keySet diff resultActivation.activeChoices.keySet
      if (unsetActivations.nonEmpty && warnUnsetActivations) {
        logger.raw.warn {
          s"""Some activation choices were left unspecified both on the commandline and in default configuration:
             |
             |  - ${activationInfo.narrow(unsetActivations).formattedChoices}
             |
             |Consider adding default choices for these to your `Activation @Id("additional")` component.
             |
             |You may do this by adding a binding for it in your`RoleAppMain#roleAppBootOverrides`, as in:
             |  ```scala
             |  override def roleAppBootOverrides(argv: ArgV): Module = new ModuleDef {
             |    make[Activation].named("additional").from {
             |      Activation(
             |        Repo -> Repo.Dummy,
             |        ...
             |      )
             |    }
             |  }
             |  ```
             |
             |You may disable this warning by setting system property `-D${DebugProperties.`izumi.distage.roles.activation.warn-unset`.name}=false`
             |""".stripMargin
        }
      }

      resultActivation
    }

    protected def configActivationSection: String = "activation"
  }

}
