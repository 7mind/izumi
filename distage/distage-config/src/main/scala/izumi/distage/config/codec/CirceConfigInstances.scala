package izumi.distage.config.codec

import java.io.File
import java.net.{InetAddress, URI, URL}
import java.nio.file.{Path, Paths}
import java.util.regex.Pattern

import com.typesafe.config.{Config, ConfigMemorySize, ConfigValue}
import io.circe.Decoder
import io.circe.`export`.Exported
import io.circe.config.syntax

import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.reflect.ClassTag
import scala.util.matching.Regex
import scala.util.{Failure, Success, Try}

object CirceConfigInstances extends CirceConfigInstances

trait CirceConfigInstances extends LowPriorityCirceConfigInstances {

  // re-export instances from io.circe.config.syntax
  // use `Exported` so that if user imports their own instances, user's instances will have higher priority

  implicit final val finiteDurationDecoder: Exported[Decoder[FiniteDuration]] = Exported(syntax.durationDecoder)
  implicit final val memorySizeDecoder: Exported[Decoder[ConfigMemorySize]] = Exported(syntax.memorySizeDecoder)
  implicit final val configDecoder: Exported[Decoder[Config]] = Exported(syntax.configDecoder)
  implicit final val configValueDecoder: Exported[Decoder[ConfigValue]] = Exported(syntax.configValueDecoder)

  // copypasta from pureconfig.BasicReaders

  implicit def javaEnumDecoder[T <: Enum[T]](implicit classTag: ClassTag[T]): Exported[Decoder[T]] =
    fromString { s =>
      val enumClass = classTag.runtimeClass.asInstanceOf[Class[T]]
      Enum.valueOf(enumClass, s)
    }

  implicit final val durationDecoder: Exported[Decoder[Duration]] = fromString[Duration](Duration.apply)

  implicit final val inetAddressConfigReader: Exported[Decoder[InetAddress]] = fromString[InetAddress](InetAddress.getByName)
  implicit final val urlConfigReader: Exported[Decoder[URL]] = fromString[URL](new URL(_))
  implicit final val pathConfigReader: Exported[Decoder[Path]] = fromString[Path](Paths.get(_))
  implicit final val fileConfigReader: Exported[Decoder[File]] = fromString[File](new File(_))
  implicit final val uriConfigReader: Exported[Decoder[URI]] = fromString[URI](new URI(_))

  implicit final val patternReader: Exported[Decoder[Pattern]] = fromString[Pattern](Pattern.compile)
  implicit final val regexReader: Exported[Decoder[Regex]] = fromString[Regex](new Regex(_))

  def fromString[T](f: String => T)(implicit classTag: ClassTag[T]): Exported[Decoder[T]] = {
    Exported(Decoder.decodeString.emapTry(str => Try(f(str)) match {
      case Failure(exception) =>
        Failure(new RuntimeException(s"Couldn't decode as ${classTag.runtimeClass.getName}", exception))
      case Success(value) =>
        Success(value)
    }))
  }

}

trait LowPriorityCirceConfigInstances {

   /** Yes, this makes circe-derivation derive recursively for configs. */
  implicit final def recursiveCirceDerivationWithConfigRule[T](implicit codec: CirceDerivationWithAdditionalConfigCodecs[T]): Exported[Decoder[T]] = {
    Exported(codec.value)
  }
}
