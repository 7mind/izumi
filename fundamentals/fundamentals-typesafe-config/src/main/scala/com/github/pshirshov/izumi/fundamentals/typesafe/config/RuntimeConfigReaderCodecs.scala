package com.github.pshirshov.izumi.fundamentals.typesafe.config

import java.io.File
import java.math.BigInteger
import java.net.{URI, URL}
import java.time.{Instant, ZoneId, ZoneOffset, _}
import java.util.UUID
import java.util.regex.Pattern

import com.github.pshirshov.izumi.fundamentals.reflection.SafeType0
import com.typesafe.config._

import scala.reflect.io.Path
import scala.reflect.runtime.{universe => ru}
import scala.util.matching.Regex

trait RuntimeConfigReaderCodecs {
   def readerCodecs: Map[SafeType0[ru.type], ConfigReader[_]]
 }

object RuntimeConfigReaderCodecs {

  final val default: RuntimeConfigReaderCodecs = new RuntimeConfigReaderCodecs {
    import ConfigReaderInstances._

    val readerCodecs: Map[SafeType0[ru.type], ConfigReader[_]] = Map(
        SafeType0[String] -> stringConfigReader
      , SafeType0[Char] -> charConfigReader
      , SafeType0[Boolean] -> booleanConfigReader
      , SafeType0[Double] -> doubleConfigReader
      , SafeType0[Float] -> floatConfigReader
      , SafeType0[Int] -> intConfigReader
      , SafeType0[Long] -> longConfigReader
      , SafeType0[Short] -> shortConfigReader

      , SafeType0[BigInteger] -> javaBigIntegerReader
      , SafeType0[java.math.BigDecimal] -> javaBigDecimalReader
      , SafeType0[BigInt] -> scalaBigIntReader
      , SafeType0[BigDecimal] -> scalaBigDecimalReader

      , SafeType0[Instant] -> instantConfigReader
      , SafeType0[ZoneOffset] -> zoneOffsetConfigReader
      , SafeType0[ZoneId] -> zoneIdConfigReader
      , SafeType0[Period] -> periodConfigReader
      , SafeType0[java.time.Duration] -> javaDurationConfigReader
      , SafeType0[Year] -> yearConfigReader

      , SafeType0[URL] -> urlConfigReader
      , SafeType0[URI] -> uriConfigReader
      , SafeType0[UUID] -> uuidConfigReader
      , SafeType0[Path] -> pathConfigReader
      , SafeType0[File] -> fileConfigReader

      , SafeType0[Pattern] -> patternReader
      , SafeType0[Regex] -> regexReader

      , SafeType0[Config] -> configConfigReader
      , SafeType0[ConfigObject] -> configObjectConfigReader
      , SafeType0[ConfigValue] -> configValueConfigReader
      , SafeType0[ConfigList] -> configListConfigReader
    )
  }

}
