package izumi.distage.roles.launcher

import distage.config.AppConfig
import izumi.distage.model.definition.{Activation, Id}
import scala.annotation.unused

trait ActivationParser extends AbstractActivationParser {}

object ActivationParser {

  class Impl(activation: Activation@Id("default")) extends ActivationParser {

    def parseActivation(@unused config: AppConfig): Activation = {
      activation
    }
  }

}
