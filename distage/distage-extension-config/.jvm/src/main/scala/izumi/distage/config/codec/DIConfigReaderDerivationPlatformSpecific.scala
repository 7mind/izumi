package izumi.distage.config.codec

import com.typesafe.config.ConfigValue
import pureconfig.ConfigReader
import pureconfig.error.ConfigReaderException

import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

private[codec] trait DIConfigReaderDerivationPlatformSpecific extends LowPriorityDIConfigReaderInstances {
  final def derived[T](implicit ct: ClassTag[T], dec: PureconfigAutoDerive[T]): DIConfigReader[T] =
    DIConfigReader.deriveFromPureconfigAutoDerive[T](using ct, dec)

  implicit final def deriveFromExistingPureconfigConfigReader[T](implicit ct: ClassTag[T], dec: ConfigReader[T]): DIConfigReader[T] = {
    useConfigReader[T](ct, dec, _)
  }

  private[codec] final def useConfigReader[T](ct: ClassTag[T], dec: ConfigReader[T], cv: ConfigValue): Try[T] = {
    dec.from(cv) match {
      case Left(errs) => Failure(ConfigReaderException[T](errs)(using ct))
      case Right(value) => Success(value)
    }
  }
}

private[codec] sealed trait LowPriorityDIConfigReaderInstances {
  implicit final def deriveFromPureconfigAutoDerive[T](implicit ct: ClassTag[T], dec: PureconfigAutoDerive[T]): DIConfigReader[T] = {
    new DIConfigReader[T] {
      override protected def decodeConfigValue(configValue: ConfigValue): Try[T] = {
        DIConfigReader.useConfigReader[T](ct, dec.value, configValue)
      }
    }
  }
}
