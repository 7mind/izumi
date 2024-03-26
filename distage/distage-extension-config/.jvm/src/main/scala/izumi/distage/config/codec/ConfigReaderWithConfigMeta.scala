package izumi.distage.config.codec

import pureconfig.ConfigReader

trait ConfigReaderWithConfigMeta[A] extends ConfigReader[A] {
  def tpe: ConfigMetaType
}
object ConfigReaderWithConfigMeta {
  def maybeFieldsFromConfigReader(configReader: ConfigReader[?]): ConfigMetaType = {
    configReader match {
      case withMeta: ConfigReaderWithConfigMeta[?] => withMeta.tpe
      case _ => ConfigMetaType.TUnknown()
    }
  }
}
