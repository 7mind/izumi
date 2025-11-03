package izumi.distage.config.codec

import io.circe.{Decoder, Json}
import izumi.distage.config.model.exceptions.DIConfigReadException
import izumi.fundamentals.preamble.toRichThrowable

import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

private[codec] trait DIConfigReaderDerivationPlatformSpecific extends LowPriorityDIConfigReaderInstances {
  final def derived[T](implicit ct: ClassTag[T], dec: CirceAutoDerive[T]): DIConfigReader[T] =
    DIConfigReader.deriveFromCirceAutoDerive[T](using ct, dec)

  implicit final def deriveFromExistingCirceDecoder[T](implicit ct: ClassTag[T], dec: Decoder[T]): DIConfigReader[T] = {
    useDecoder[T](ct, dec, _)
  }

  private[codec] final def useDecoder[T](ct: ClassTag[T], dec: Decoder[T], json: Json): Try[T] = {
    dec.decodeJson(json) match {
      case Left(err) => Failure(new DIConfigReadException(s"Failed to decode config ${ct.runtimeClass.getName}: ${err.stacktraceString}", err))
      case Right(value) => Success(value)
    }
  }
}

private[codec] sealed trait LowPriorityDIConfigReaderInstances {
  implicit final def deriveFromCirceAutoDerive[T](implicit ct: ClassTag[T], dec: CirceAutoDerive[T]): DIConfigReader[T] = {
    new DIConfigReader[T] {
      override protected def decodeConfigValue(json: Json): Try[T] = {
        DIConfigReader.useDecoder[T](ct, dec.value, json)
      }
    }
  }
}
