package izumi.distage.config.codec

import pureconfig.{ConfigCursor, ConfigReader, ReadsMissingKeys}

trait PureconfigSharedInstances {

  implicit final def optionReaderWithFields[A](implicit configReader: ConfigReader[A]): ConfigReader[Option[A]] = {
    val inner = ConfigReader.optionReader[A](configReader)

    new ConfigReader[Option[A]] with ReadsMissingKeys {
      override def from(cur: ConfigCursor): ConfigReader.Result[Option[A]] = inner.from(cur)
    }
  }

}
