package izumi.distage.roles.launcher

import distage.config.AppConfig
import izumi.distage.model.definition.Activation

trait AbstractActivationParser {
  def parseActivation(config: AppConfig): Activation
}
