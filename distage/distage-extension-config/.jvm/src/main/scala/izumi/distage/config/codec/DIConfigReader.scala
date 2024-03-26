package izumi.distage.config.codec

import com.typesafe.config.ConfigException.Missing
import com.typesafe.config.*
import izumi.distage.config.DistageConfigImpl
import izumi.distage.config.model.exceptions.DIConfigReadException
import izumi.reflect.Tag
import pureconfig.ConfigReader
import pureconfig.error.ConfigReaderException

import java.math.{BigDecimal as JavaBigDecimal, BigInteger}
import java.net.{URI, URL}
import java.nio.file.Path
import java.time.temporal.ChronoUnit
import java.time.{Duration as JavaDuration, Instant, Period, Year, ZoneId, ZoneOffset}
import java.util.UUID
import java.util.regex.Pattern
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.reflect.{ClassTag, classTag}
import scala.util.matching.Regex
import scala.util.{Failure, Success, Try}

/**
  * Config reader that uses a [[pureconfig.ConfigReader pureconfig.ConfigReader]] implicit instance for a type
  * to decode it from Typesafe Config.
  *
  * Always automatically derives a codec if it's not available.
  *
  * Automatic derivation will use **`camelCase`** fields, NOT `kebab-case` fields,
  * as in default pureconfig. It also overrides pureconfig's default `type` field
  * type discriminator for sealed traits, instead using a `circe`-like format with a single-key object.
  *
  * Example:
  *
  * {{{
  *   sealed trait AorB
  *   final case class A(a: Int) extends AorB
  *   final case class B(b: String) extends AorB
  *
  *   final case class Config(values: List[AorB])
  * }}}
  *
  * In config:
  *
  * {{{
  *   config {
  *     values = [
  *       { A { a = 123 } },
  *       { B { b = cba } }
  *     ]
  *   }
  * }}}
  *
  * Auto-derivation will work without importing `pureconfig.generic.auto._` and without any other imports
  *
  * You may use [[izumi.distage.config.codec.PureconfigAutoDerive]] f you want to use `DIConfigReader`'s deriving strategy to derive a standalone `pureconfig` codec:
  *
  * {{{
  *   final case class Abc(a: Duration, b: Regex, c: URL)
  *
  *   object Abc {
  *     implicit val configReader: pureconfig.ConfigReader[Abc] =
  *       PureconfigAutoDerive[Abc]
  *   }
  * }}}
  */
trait DIConfigReader[A] extends AbstractDIConfigReader[A] {
  protected def decodeConfigValue(configValue: ConfigValue): Try[A]

  def tpe: ConfigMetaType

  final def decodeConfig(config: DistageConfigImpl): Try[A] = decodeConfigValue(config.root())

  final def decodeConfig(path: String)(config: DistageConfigImpl)(implicit tag: Tag[A]): A = {
    unpackResult(config, path)(decodeConfigValue(config.getValue(path)))
  }

  final def map[B](f: A => B): DIConfigReader[B] = new DIConfigReader[B] {
    override protected def decodeConfigValue(configValue: ConfigValue): Try[B] = DIConfigReader.this.decodeConfigValue(configValue).map(f)

    override def tpe: ConfigMetaType = DIConfigReader.this.tpe
  }

  final def flatMap[B](f: A => DIConfigReader[B]): DIConfigReader[B] = new DIConfigReader[B] {
    override protected def decodeConfigValue(configValue: ConfigValue): Try[B] =
      DIConfigReader.this.decodeConfigValue(configValue).flatMap(f(_).decodeConfigValue(configValue))

    override def tpe: ConfigMetaType = DIConfigReader.this.tpe
  }

  final def decodeConfigWithDefault(path: String)(default: => A)(config: DistageConfigImpl)(implicit tag: Tag[A]): A = {
    try {
      val cv = config.getValue(path)
      unpackResult(config, path)(decodeConfigValue(cv))
    } catch {
      case _: Missing => default
    }
  }

  private[this] def unpackResult[T: Tag](config: DistageConfigImpl, path: String)(t: => Try[T]): T = {
    Try(t).flatten match {
      case Failure(exception) =>
        throw new DIConfigReadException(
          s"""Couldn't read configuration type at path="$path" as type `${Tag[T].tag}` due to error:
             |
             |- ${exception.getMessage}
             |
             |Config was: ${config.origin().description()}
             |""".stripMargin,
          exception,
        )
      case Success(value) =>
        value
    }
  }
}

object DIConfigReader extends LowPriorityDIConfigReaderInstances {
  @inline def apply[T: DIConfigReader]: DIConfigReader[T] = implicitly

  def derived[T: ClassTag](implicit dec: PureconfigAutoDerive[T]): DIConfigReader[T] =
    DIConfigReader.deriveFromPureconfigAutoDerive[T](classTag[T], dec)

    implicit def deriveList[T: ClassTag](implicit dec: DIConfigReader[T], listDec: ConfigReader[List[T]]): DIConfigReader[List[T]] = new DIConfigReader[List[T]] {
      override protected def decodeConfigValue(configValue: ConfigValue): Try[List[T]] = useConfigReader[List[T]](listDec, configValue)

      override def tpe: ConfigMetaType = ConfigMetaType.TList(dec.tpe)
    }

  implicit def deriveMap[K: ClassTag, V: ClassTag](
    implicit decK: DIConfigReader[K],
    decV: DIConfigReader[V],
    mapDec: ConfigReader[Map[K, V]],
  ): DIConfigReader[Map[K, V]] =
    new DIConfigReader[Map[K, V]] {
      override protected def decodeConfigValue(configValue: ConfigValue): Try[Map[K, V]] = useConfigReader[Map[K, V]](mapDec, configValue)

      override def tpe: ConfigMetaType = ConfigMetaType.TMap(decK.tpe, decV.tpe)
    }

  def fromBasic[T: ClassTag](tpeBasic: ConfigMetaBasicType)(implicit dec: ConfigReader[T]): DIConfigReader[T] =
    new DIConfigReader[T] {
      override protected def decodeConfigValue(configValue: ConfigValue): Try[T] = useConfigReader[T](dec, configValue)

      override def tpe: ConfigMetaType = ConfigMetaType.TBasic(tpeBasic)
    }

  implicit def read_String: DIConfigReader[String] = fromBasic[String](ConfigMetaBasicType.TString)
  implicit def read_Char: DIConfigReader[Char] = fromBasic[Char](ConfigMetaBasicType.TChar)
  implicit def read_Boolean: DIConfigReader[Boolean] = fromBasic[Boolean](ConfigMetaBasicType.TBoolean)
  implicit def read_Double: DIConfigReader[Double] = fromBasic[Double](ConfigMetaBasicType.TDouble)
  implicit def read_Float: DIConfigReader[Float] = fromBasic[Float](ConfigMetaBasicType.TFloat)
  implicit def read_Int: DIConfigReader[Int] = fromBasic[Int](ConfigMetaBasicType.TInt)
  implicit def read_Long: DIConfigReader[Long] = fromBasic[Long](ConfigMetaBasicType.TLong)
  implicit def read_Short: DIConfigReader[Short] = fromBasic[Short](ConfigMetaBasicType.TShort)
  implicit def read_Byte: DIConfigReader[Byte] = fromBasic[Byte](ConfigMetaBasicType.TByte)
  implicit def read_URL: DIConfigReader[URL] = fromBasic[URL](ConfigMetaBasicType.TURL)
  implicit def read_UUID: DIConfigReader[UUID] = fromBasic[UUID](ConfigMetaBasicType.TUUID)
  implicit def read_Path: DIConfigReader[Path] = fromBasic[Path](ConfigMetaBasicType.TPath)
  implicit def read_URI: DIConfigReader[URI] = fromBasic[URI](ConfigMetaBasicType.TURI)
  implicit def read_Pattern: DIConfigReader[Pattern] = fromBasic[Pattern](ConfigMetaBasicType.TPattern)
  implicit def read_Regex: DIConfigReader[Regex] = fromBasic[Regex](ConfigMetaBasicType.TRegex)
  implicit def read_Instant: DIConfigReader[Instant] = fromBasic[Instant](ConfigMetaBasicType.TInstant)
  implicit def read_ZoneOffset: DIConfigReader[ZoneOffset] = fromBasic[ZoneOffset](ConfigMetaBasicType.TZoneOffset)
  implicit def read_ZoneId: DIConfigReader[ZoneId] = fromBasic[ZoneId](ConfigMetaBasicType.TZoneId)
  implicit def read_Period: DIConfigReader[Period] = fromBasic[Period](ConfigMetaBasicType.TPeriod)
  implicit def read_ChronoUnit: DIConfigReader[ChronoUnit] = fromBasic[ChronoUnit](ConfigMetaBasicType.TChronoUnit)
  implicit def read_JavaDuration: DIConfigReader[JavaDuration] = fromBasic[JavaDuration](ConfigMetaBasicType.TJavaDuration)
  implicit def read_Year: DIConfigReader[Year] = fromBasic[Year](ConfigMetaBasicType.TYear)
  implicit def read_Duration: DIConfigReader[Duration] = fromBasic[Duration](ConfigMetaBasicType.TDuration)
  implicit def read_FiniteDuration: DIConfigReader[FiniteDuration] = fromBasic[FiniteDuration](ConfigMetaBasicType.TFiniteDuration)
  implicit def read_BigInteger: DIConfigReader[BigInteger] = fromBasic[BigInteger](ConfigMetaBasicType.TBigInteger)
  implicit def read_BigInt: DIConfigReader[BigInt] = fromBasic[BigInt](ConfigMetaBasicType.TBigInt)
  implicit def read_BigDecimal: DIConfigReader[BigDecimal] = fromBasic[BigDecimal](ConfigMetaBasicType.TBigDecimal)
  implicit def read_Config: DIConfigReader[Config] = fromBasic[Config](ConfigMetaBasicType.TConfig)
  implicit def read_ConfigObject: DIConfigReader[ConfigObject] = fromBasic[ConfigObject](ConfigMetaBasicType.TConfigObject)
  implicit def read_ConfigValue: DIConfigReader[ConfigValue] = fromBasic[ConfigValue](ConfigMetaBasicType.TConfigValue)
  implicit def read_ConfigList: DIConfigReader[ConfigList] = fromBasic[ConfigList](ConfigMetaBasicType.TConfigList)
  implicit def read_ConfigMemorySize: DIConfigReader[ConfigMemorySize] = fromBasic[ConfigMemorySize](ConfigMetaBasicType.TConfigMemorySize)
  implicit def read_JavaBigDecimal: DIConfigReader[JavaBigDecimal] = fromBasic[JavaBigDecimal](ConfigMetaBasicType.TJavaBigDecimal)

  private[codec] def useConfigReader[T: ClassTag](dec: ConfigReader[T], cv: ConfigValue): Try[T] = {
    dec.from(cv) match {
      case Left(errs) => Failure(ConfigReaderException[T](errs))
      case Right(value) => Success(value)
    }
  }
}

sealed trait LowPriorityDIConfigReaderInstances {
  implicit final def deriveFromPureconfigAutoDerive[T: ClassTag](implicit dec: PureconfigAutoDerive[T]): DIConfigReader[T] = {
    new DIConfigReader[T] {
      override protected def decodeConfigValue(configValue: ConfigValue): Try[T] = DIConfigReader.useConfigReader[T](dec.value, configValue)
      override val tpe: ConfigMetaType = dec.tpe
    }
  }
}
