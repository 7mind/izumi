package izumi.distage.config.model

import izumi.distage.config.codec.ConfigMeta
import izumi.distage.model.definition.BindingTag

final case class ConfTag(
  confPath: String
)(/* excluded from equals/hashCode */
  val parser: AppConfig => Any,
  val fieldsMeta: ConfigMeta,
) extends BindingTag
