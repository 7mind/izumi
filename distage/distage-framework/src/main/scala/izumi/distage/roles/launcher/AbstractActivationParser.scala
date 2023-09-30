package izumi.distage.roles.launcher

import izumi.distage.model.definition.Activation

trait AbstractActivationParser {
  def parseActivation(): Activation
}
