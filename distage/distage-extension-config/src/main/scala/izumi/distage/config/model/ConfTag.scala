package izumi.distage.config.model

import izumi.distage.model.definition.BindingTag

final case class ConfTag(confPath: String)(val parser: AppConfig => Any) extends BindingTag
