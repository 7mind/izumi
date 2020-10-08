package izumi.distage.roles.launcher

import distage.Id
import distage.config.AppConfig
import izumi.distage.framework.model.ActivationInfo
import izumi.distage.model.definition.Activation
import izumi.distage.roles.RoleAppMain
import izumi.fundamentals.platform.cli.model.raw.RawAppArgs
import izumi.fundamentals.platform.strings.IzString.toRichString
import logstage.IzLogger

/**
  * Note, besides replacing this class, activation parsing strategy can also be changed by using bootstrap modules or plugins
  * and adding an override for `make[Activation].named("primary")` to [[izumi.distage.roles.RoleAppMain#makeAppModuleOverride]]
  */
trait ActivationParser {
  def parseActivation(): Activation
}

object ActivationParser {
  class Impl(
    lateLogger: IzLogger,
    parameters: RawAppArgs,
    config: AppConfig,
    activationInfo: ActivationInfo,
    defaultActivations: Activation @Id("main"),
    requiredActivations: Activation @Id("additional"),
  ) extends ActivationParser {

    def parseActivation(): Activation = {
      val parser = new RoleAppActivationParser.Impl(lateLogger)

      val cmdChoices = parameters.globalParameters.findValues(RoleAppMain.Options.use).map(_.value.split2(':'))
      val cmdActivations = parser.parseActivation(cmdChoices, activationInfo)

      val configChoices = if (config.config.hasPath(configActivationSection)) {
        ActivationConfig.diConfigReader.decodeConfig(configActivationSection)(config.config).activation
      } else Map.empty
      val configActivations = parser.parseActivation(configChoices, activationInfo)

      defaultActivations ++ requiredActivations ++ configActivations ++ cmdActivations // commandline choices override values in config
    }

    protected def configActivationSection: String = "activation"

  }
}
