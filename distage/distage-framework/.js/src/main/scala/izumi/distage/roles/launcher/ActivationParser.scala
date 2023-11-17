package izumi.distage.roles.launcher

import distage.config.AppConfig
import izumi.distage.model.definition.{Activation, Id}
import scala.annotation.unused

trait ActivationParser extends AbstractActivationParser {}

object ActivationParser {

  class Impl(
              defaultActivations: Activation@Id("default"),
              additionalActivations: Activation@Id("additional"),
              activation: Activation@Id("entrypoint"),
            ) extends ActivationParser {
    def parseActivation(@unused config: AppConfig): Activation = {
      defaultActivations ++
        additionalActivations ++
        activation
    }
  }

}
