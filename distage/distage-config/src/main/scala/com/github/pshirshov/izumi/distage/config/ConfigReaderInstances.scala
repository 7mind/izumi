package com.github.pshirshov.izumi.distage.config

import java.io.File
import java.math.{BigInteger, MathContext}
import java.net.{URI, URL}
import java.nio.file.{Path, Paths}
import java.time._
import java.util.UUID
import java.util.regex.Pattern

import com.typesafe.config.{Config, ConfigList, ConfigObject, ConfigValue}

import scala.math.{BigDecimal, BigInt}
import scala.util.matching.Regex
import scala.util.{Success, Try}
import java.time.{Duration => JavaDuration}
import java.math.{BigDecimal => JavaBigDecimal}

import scala.reflect.ClassTag

// copypasta from pureconfig.BasicReaders

object ConfigReaderInstances {

  implicit final val stringConfigReader: ConfigReader[String] = ConfigReader.fromString[String](s => s)
  implicit final val charConfigReader: ConfigReader[Char] = ConfigReader.fromString[Char](s =>
    s.length match {
      case 1 => s.charAt(0)
      case len => throw new ConfigReadException(s"Expected a single Char, but string `$s` is more than 1 character long.")
    })
  implicit final val booleanConfigReader: ConfigReader[Boolean] = ConfigReader.fromString[Boolean]{
    case "yes" | "on" => true
    case "no" | "off" => false
    case other => other.toBoolean
  }
  implicit final val doubleConfigReader: ConfigReader[Double] = ConfigReader.fromString[Double]{
    case v if v.last == '%' => v.dropRight(1).toDouble / 100d
    case v => v.toDouble
  }
  implicit final val floatConfigReader: ConfigReader[Float] = ConfigReader.fromString[Float]{
    case v if v.last == '%' => v.dropRight(1).toFloat / 100f
    case v => v.toFloat
  }
  implicit final val intConfigReader: ConfigReader[Int] = ConfigReader.fromString[Int](_.toInt)
  implicit final val longConfigReader: ConfigReader[Long] = ConfigReader.fromString[Long](_.toLong)
  implicit final val shortConfigReader: ConfigReader[Short] = ConfigReader.fromString[Short](_.toShort)

  implicit def javaEnumReader[T <: Enum[T]](implicit tag: ClassTag[T]): ConfigReader[T] =
    ConfigReader.fromString { s =>
      val enumClass = tag.runtimeClass.asInstanceOf[Class[T]]
      Enum.valueOf(enumClass, s)
    }

  implicit final val urlConfigReader: ConfigReader[URL] = ConfigReader.fromString[URL](new URL(_))
  implicit final val uuidConfigReader: ConfigReader[UUID] = ConfigReader.fromString[UUID](UUID.fromString)
  implicit final val pathConfigReader: ConfigReader[Path] = ConfigReader.fromString[Path](Paths.get(_))
  implicit final val fileConfigReader: ConfigReader[File] = ConfigReader.fromString[File](new File(_))
  implicit final val uriConfigReader: ConfigReader[URI] = ConfigReader.fromString[URI](new URI(_))

  implicit final val patternReader: ConfigReader[Pattern] = ConfigReader.fromString[Pattern](Pattern.compile)
  implicit final val regexReader: ConfigReader[Regex] = ConfigReader.fromString[Regex](new Regex(_))

  implicit final val instantConfigReader: ConfigReader[Instant] =
    ConfigReader.fromString[Instant](Instant.parse)

  implicit final val zoneOffsetConfigReader: ConfigReader[ZoneOffset] =
    ConfigReader.fromString[ZoneOffset](ZoneOffset.of)

  implicit final val zoneIdConfigReader: ConfigReader[ZoneId] =
    ConfigReader.fromString[ZoneId](ZoneId.of)

  implicit final val periodConfigReader: ConfigReader[Period] =
    ConfigReader.fromString[Period](Period.parse)

  implicit final val javaDurationConfigReader: ConfigReader[JavaDuration] =
    ConfigReader.fromString[JavaDuration](JavaDuration.parse)

  implicit final val yearConfigReader: ConfigReader[Year] =
    ConfigReader.fromString[Year](Year.parse)

  // BigDecimal String parsing copied from Scala 2.11.
  // The Scala 2.10 bigDecimal class is hard-coded to use 128-bits of prevision when parsing BigDecimal from String.
  // This causes truncation for large values.
  // Scala 2.11+ fixed this bug by adapting precision to accommodate all digits in the string.
  private def exact(repr: JavaBigDecimal): BigDecimal = {
    val mc =
      if (repr.precision <= BigDecimal.defaultMathContext.getPrecision) BigDecimal.defaultMathContext
      else new MathContext(repr.precision, java.math.RoundingMode.HALF_EVEN)
    new BigDecimal(repr, mc)
  }

  implicit final val javaBigIntegerReader: ConfigReader[BigInteger] =
    ConfigReader.fromString[BigInteger](new BigInteger(_))

  implicit final val javaBigDecimalReader: ConfigReader[JavaBigDecimal] =
    ConfigReader.fromString[JavaBigDecimal](new JavaBigDecimal(_))

  implicit final val scalaBigIntReader: ConfigReader[BigInt] =
    ConfigReader.fromString[BigInt](BigInt(_))

  implicit final val scalaBigDecimalReader: ConfigReader[BigDecimal] =
    ConfigReader.fromString[BigDecimal](s => exact(new JavaBigDecimal(s)))

  implicit final val configConfigReader: ConfigReader[Config] =
    cv => Try(cv.asInstanceOf[ConfigObject].toConfig)

  implicit final val configObjectConfigReader: ConfigReader[ConfigObject] =
    cv => Try(cv.asInstanceOf[ConfigObject])

  implicit final val configValueConfigReader: ConfigReader[ConfigValue] =
    Success(_)

  implicit final val configListConfigReader: ConfigReader[ConfigList] =
    cv => Try(cv.asInstanceOf[ConfigList])

}
