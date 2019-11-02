package izumi.distage.config

import izumi.distage.config.ConfigProvider.ConfigImport

trait ConfigValueTransformer {
  def transform: PartialFunction[(ConfigImport, Any), Any]
}

object ConfigValueTransformer {

  object Null extends ConfigValueTransformer {
    override def transform: PartialFunction[(ConfigImport, Any), Any] = {
      case (_, value) =>
        value
    }
  }

}
