package izumi.distage.config.model

import izumi.distage.config.codec.ConfigMetaType
import izumi.distage.model.definition.BindingTag

final case class ConfTag(
  confPath: String,
  parser: AppConfig => Any,
  tpe: ConfigMetaType,
) extends BindingTag
