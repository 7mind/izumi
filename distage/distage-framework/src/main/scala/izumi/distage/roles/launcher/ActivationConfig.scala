package izumi.distage.roles.launcher

import izumi.distage.config.codec.DIConfigReader

final case class ActivationConfig(activation: Map[String, String]) extends AnyVal

object ActivationConfig {
  implicit val diConfigReader: DIConfigReader[ActivationConfig] = DIConfigReader[Map[String, String]].map(ActivationConfig(_))
}
