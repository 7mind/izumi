package izumi.distage.config.model

import izumi.distage.config.codec.ConfigMetaType
import izumi.distage.model.definition.BindingTag

final case class ConfTag(
  confPath: String
)(/* excluded from equals/hashCode */
  val parser: AppConfig => Any,
  val tpe: ConfigMetaType,
) extends BindingTag
