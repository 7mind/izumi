package izumi.distage.config.codec

import pureconfig.{ConfigCursor, ConfigReader, ReadsMissingKeys}

trait PureconfigSharedInstances {

  implicit final def optionReaderWithFields[A](implicit configReader: ConfigReader[A]): ConfigReaderWithConfigMeta[Option[A]] = {
    val inner = ConfigReader.optionReader[A](configReader)
    val fields = ConfigReaderWithConfigMeta.maybeFieldsFromConfigReader(configReader)
    new ConfigReaderWithConfigMeta[Option[A]] with ReadsMissingKeys {
      override def from(cur: ConfigCursor): ConfigReader.Result[Option[A]] = inner.from(cur)

      override def tpe: ConfigMetaType = fields
    }
  }

}
