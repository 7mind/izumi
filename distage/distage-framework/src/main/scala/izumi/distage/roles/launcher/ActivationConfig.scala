package izumi.distage.roles.launcher

import izumi.distage.config.codec.DIConfigReader

final case class ActivationConfig(choices: Map[String, String])

object ActivationConfig {
  implicit val diConfigReader: DIConfigReader[ActivationConfig] = DIConfigReader[Map[String, String]].map(ActivationConfig(_))
}
