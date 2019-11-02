package izumi.fundamentals.typesafe.config

import izumi.fundamentals.reflection.SafeType0
import com.typesafe.config._

import scala.concurrent.duration.{Duration, FiniteDuration}
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

      , SafeType0.get[java.math.BigInteger] -> javaBigIntegerReader
      , SafeType0.get[java.math.BigDecimal] -> javaBigDecimalReader
      , SafeType0.get[BigInt] -> scalaBigIntReader
      , SafeType0.get[BigDecimal] -> scalaBigDecimalReader

      , SafeType0.get[Duration] -> durationConfigReader
      , SafeType0.get[FiniteDuration] -> finiteDurationConfigReader

      , SafeType0.get[java.time.Instant] -> instantConfigReader
      , SafeType0.get[java.time.ZoneOffset] -> zoneOffsetConfigReader
      , SafeType0.get[java.time.ZoneId] -> zoneIdConfigReader
      , SafeType0.get[java.time.Period] -> periodConfigReader
      , SafeType0.get[java.time.Duration] -> javaDurationConfigReader
      , SafeType0.get[java.time.Year] -> yearConfigReader

      , SafeType0.get[java.net.URL] -> urlConfigReader
      , SafeType0.get[java.net.URI] -> uriConfigReader
      , SafeType0.get[java.util.UUID] -> uuidConfigReader
      , SafeType0.get[java.nio.file.Path] -> pathConfigReader
      , SafeType0.get[java.io.File] -> fileConfigReader
      , SafeType0.get[java.net.InetAddress] -> inetAddressConfigReader

      , SafeType0.get[java.util.regex.Pattern] -> patternReader
      , SafeType0.get[Regex] -> regexReader

      , SafeType0.get[Config] -> configConfigReader
      , SafeType0.get[ConfigObject] -> configObjectConfigReader
      , SafeType0.get[ConfigValue] -> configValueConfigReader
      , SafeType0.get[ConfigList] -> configListConfigReader
    )
  }

}
