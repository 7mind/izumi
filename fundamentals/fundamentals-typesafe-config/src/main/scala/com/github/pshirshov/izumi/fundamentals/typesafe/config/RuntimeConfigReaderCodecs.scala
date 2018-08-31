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
        SafeType0.get[String] -> stringConfigReader
      , SafeType0.get[Char] -> charConfigReader
      , SafeType0.get[Boolean] -> booleanConfigReader
      , SafeType0.get[Double] -> doubleConfigReader
      , SafeType0.get[Float] -> floatConfigReader
      , SafeType0.get[Int] -> intConfigReader
      , SafeType0.get[Long] -> longConfigReader
      , SafeType0.get[Short] -> shortConfigReader

      , SafeType0.get[BigInteger] -> javaBigIntegerReader
      , SafeType0.get[java.math.BigDecimal] -> javaBigDecimalReader
      , SafeType0.get[BigInt] -> scalaBigIntReader
      , SafeType0.get[BigDecimal] -> scalaBigDecimalReader

      , SafeType0.get[Instant] -> instantConfigReader
      , SafeType0.get[ZoneOffset] -> zoneOffsetConfigReader
      , SafeType0.get[ZoneId] -> zoneIdConfigReader
      , SafeType0.get[Period] -> periodConfigReader
      , SafeType0.get[java.time.Duration] -> javaDurationConfigReader
      , SafeType0.get[Year] -> yearConfigReader

      , SafeType0.get[URL] -> urlConfigReader
      , SafeType0.get[URI] -> uriConfigReader
      , SafeType0.get[UUID] -> uuidConfigReader
      , SafeType0.get[Path] -> pathConfigReader
      , SafeType0.get[File] -> fileConfigReader

      , SafeType0.get[Pattern] -> patternReader
      , SafeType0.get[Regex] -> regexReader

      , SafeType0.get[Config] -> configConfigReader
      , SafeType0.get[ConfigObject] -> configObjectConfigReader
      , SafeType0.get[ConfigValue] -> configValueConfigReader
      , SafeType0.get[ConfigList] -> configListConfigReader
    )
  }

}
