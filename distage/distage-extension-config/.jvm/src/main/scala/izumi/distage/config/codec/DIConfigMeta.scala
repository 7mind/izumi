package izumi.distage.config.codec

import com.typesafe.config.*

import java.math.{BigDecimal as JavaBigDecimal, BigInteger}
import java.net.{URI, URL}
import java.nio.file.Path
import java.time.temporal.ChronoUnit
import java.time.{Duration as JavaDuration, Instant, Period, Year, ZoneId, ZoneOffset}
import java.util.UUID
import java.util.regex.Pattern
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.util.matching.Regex

trait DIConfigMeta[T] {
  def tpe: ConfigMetaType
}

object DIConfigMeta extends LowPriorityDIConfigMetaInstances {
  def apply[T](configMetaType: ConfigMetaType): DIConfigMeta[T] = new DIConfigMeta[T] {
    override def tpe: ConfigMetaType = configMetaType
  }

  implicit def deriveSeq[T, S[K] <: scala.collection.Seq[K]](implicit m: DIConfigMeta[T]): DIConfigMeta[S[T]] = new DIConfigMeta[S[T]] {
    override def tpe: ConfigMetaType = ConfigMetaType.TList(m.tpe)
  }

  implicit def deriveSet[T, S[K] <: scala.collection.Set[K]](implicit m: DIConfigMeta[T]): DIConfigMeta[S[T]] = new DIConfigMeta[S[T]] {
    override def tpe: ConfigMetaType = ConfigMetaType.TSet(m.tpe)
  }

  implicit def deriveOption[T](implicit m: DIConfigMeta[T]): DIConfigMeta[Option[T]] = new DIConfigMeta[Option[T]] {
    override def tpe: ConfigMetaType = ConfigMetaType.TOption(m.tpe)
  }

  implicit def deriveMap[K, V, M[A, B] <: scala.collection.Map[A, B]](
    implicit decK: DIConfigMeta[K],
    decV: DIConfigMeta[V],
  ): DIConfigMeta[M[K, V]] =
    new DIConfigMeta[M[K, V]] {
      override def tpe: ConfigMetaType = ConfigMetaType.TMap(decK.tpe, decV.tpe)
    }

  def fromBasic[T](tpeBasic: ConfigMetaBasicType): DIConfigMeta[T] = new DIConfigMeta[T] {
    override def tpe: ConfigMetaType = ConfigMetaType.TBasic(tpeBasic)
  }

  implicit def read_String: DIConfigMeta[String] = fromBasic[String](ConfigMetaBasicType.TString)
  implicit def read_Char: DIConfigMeta[Char] = fromBasic[Char](ConfigMetaBasicType.TChar)
  implicit def read_Boolean: DIConfigMeta[Boolean] = fromBasic[Boolean](ConfigMetaBasicType.TBoolean)
  implicit def read_Double: DIConfigMeta[Double] = fromBasic[Double](ConfigMetaBasicType.TDouble)
  implicit def read_Float: DIConfigMeta[Float] = fromBasic[Float](ConfigMetaBasicType.TFloat)
  implicit def read_Int: DIConfigMeta[Int] = fromBasic[Int](ConfigMetaBasicType.TInt)
  implicit def read_Long: DIConfigMeta[Long] = fromBasic[Long](ConfigMetaBasicType.TLong)
  implicit def read_Short: DIConfigMeta[Short] = fromBasic[Short](ConfigMetaBasicType.TShort)
  implicit def read_Byte: DIConfigMeta[Byte] = fromBasic[Byte](ConfigMetaBasicType.TByte)
  implicit def read_URL: DIConfigMeta[URL] = fromBasic[URL](ConfigMetaBasicType.TURL)
  implicit def read_UUID: DIConfigMeta[UUID] = fromBasic[UUID](ConfigMetaBasicType.TUUID)
  implicit def read_Path: DIConfigMeta[Path] = fromBasic[Path](ConfigMetaBasicType.TPath)
  implicit def read_URI: DIConfigMeta[URI] = fromBasic[URI](ConfigMetaBasicType.TURI)
  implicit def read_Pattern: DIConfigMeta[Pattern] = fromBasic[Pattern](ConfigMetaBasicType.TPattern)
  implicit def read_Regex: DIConfigMeta[Regex] = fromBasic[Regex](ConfigMetaBasicType.TRegex)
  implicit def read_Instant: DIConfigMeta[Instant] = fromBasic[Instant](ConfigMetaBasicType.TInstant)
  implicit def read_ZoneOffset: DIConfigMeta[ZoneOffset] = fromBasic[ZoneOffset](ConfigMetaBasicType.TZoneOffset)
  implicit def read_ZoneId: DIConfigMeta[ZoneId] = fromBasic[ZoneId](ConfigMetaBasicType.TZoneId)
  implicit def read_Period: DIConfigMeta[Period] = fromBasic[Period](ConfigMetaBasicType.TPeriod)
  implicit def read_ChronoUnit: DIConfigMeta[ChronoUnit] = fromBasic[ChronoUnit](ConfigMetaBasicType.TChronoUnit)
  implicit def read_JavaDuration: DIConfigMeta[JavaDuration] = fromBasic[JavaDuration](ConfigMetaBasicType.TJavaDuration)
  implicit def read_Year: DIConfigMeta[Year] = fromBasic[Year](ConfigMetaBasicType.TYear)
  implicit def read_Duration: DIConfigMeta[Duration] = fromBasic[Duration](ConfigMetaBasicType.TDuration)
  implicit def read_FiniteDuration: DIConfigMeta[FiniteDuration] = fromBasic[FiniteDuration](ConfigMetaBasicType.TFiniteDuration)
  implicit def read_BigInteger: DIConfigMeta[BigInteger] = fromBasic[BigInteger](ConfigMetaBasicType.TBigInteger)
  implicit def read_BigInt: DIConfigMeta[BigInt] = fromBasic[BigInt](ConfigMetaBasicType.TBigInt)
  implicit def read_BigDecimal: DIConfigMeta[BigDecimal] = fromBasic[BigDecimal](ConfigMetaBasicType.TBigDecimal)
  implicit def read_Config: DIConfigMeta[Config] = fromBasic[Config](ConfigMetaBasicType.TConfig)
  implicit def read_ConfigObject: DIConfigMeta[ConfigObject] = fromBasic[ConfigObject](ConfigMetaBasicType.TConfigObject)
  implicit def read_ConfigValue: DIConfigMeta[ConfigValue] = fromBasic[ConfigValue](ConfigMetaBasicType.TConfigValue)
  implicit def read_ConfigList: DIConfigMeta[ConfigList] = fromBasic[ConfigList](ConfigMetaBasicType.TConfigList)
  implicit def read_ConfigMemorySize: DIConfigMeta[ConfigMemorySize] = fromBasic[ConfigMemorySize](ConfigMetaBasicType.TConfigMemorySize)
  implicit def read_JavaBigDecimal: DIConfigMeta[JavaBigDecimal] = fromBasic[JavaBigDecimal](ConfigMetaBasicType.TJavaBigDecimal)

}

sealed trait LowPriorityDIConfigMetaInstances {
  implicit def derived[T](implicit dec: MetaAutoDerive[T]): DIConfigMeta[T] =
    dec.value
}
