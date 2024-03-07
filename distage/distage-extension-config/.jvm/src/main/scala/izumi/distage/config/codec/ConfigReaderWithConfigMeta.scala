package izumi.distage.config.codec

import pureconfig.ConfigReader

trait ConfigReaderWithConfigMeta[A] extends ConfigReader[A] {
  def fieldsMeta: ConfigMeta
}
object ConfigReaderWithConfigMeta {
  def maybeFieldsFromConfigReader(configReader: ConfigReader[?]): ConfigMeta = {
    configReader match {
      case withMeta: ConfigReaderWithConfigMeta[?] => withMeta.fieldsMeta
      case _ => ConfigMeta.ConfigMetaUnknown()
    }
  }
}
