package izumi.distage.config.codec

sealed trait ConfigMetaType
object ConfigMetaType {
  final case class TCaseClass(fields: Seq[(String, ConfigMetaType)]) extends ConfigMetaType
  final case class TSealedTrait(branches: Set[(String, ConfigMetaType)]) extends ConfigMetaType
  final case class TBasic(tpe: ConfigMetaBasicType) extends ConfigMetaType
  final case class TList(tpe: ConfigMetaType) extends ConfigMetaType
  final case class TSet(tpe: ConfigMetaType) extends ConfigMetaType
  final case class TOption(tpe: ConfigMetaType) extends ConfigMetaType
  final case class TMap(keyType: ConfigMetaType, valueType: ConfigMetaType) extends ConfigMetaType
  final case class TUnknown(source: String = "???") extends ConfigMetaType
}

sealed trait ConfigMetaBasicType

object ConfigMetaBasicType {
  case object TString extends ConfigMetaBasicType
  case object TChar extends ConfigMetaBasicType
  case object TBoolean extends ConfigMetaBasicType
  case object TDouble extends ConfigMetaBasicType
  case object TFloat extends ConfigMetaBasicType
  case object TInt extends ConfigMetaBasicType
  case object TLong extends ConfigMetaBasicType
  case object TShort extends ConfigMetaBasicType
  case object TByte extends ConfigMetaBasicType
  case object TURL extends ConfigMetaBasicType
  case object TUUID extends ConfigMetaBasicType
  case object TPath extends ConfigMetaBasicType
  case object TURI extends ConfigMetaBasicType
  case object TPattern extends ConfigMetaBasicType
  case object TRegex extends ConfigMetaBasicType
  case object TInstant extends ConfigMetaBasicType
  case object TZoneOffset extends ConfigMetaBasicType
  case object TZoneId extends ConfigMetaBasicType
  case object TPeriod extends ConfigMetaBasicType
  case object TChronoUnit extends ConfigMetaBasicType
  case object TJavaDuration extends ConfigMetaBasicType
  case object TYear extends ConfigMetaBasicType
  case object TDuration extends ConfigMetaBasicType
  case object TFiniteDuration extends ConfigMetaBasicType
  case object TBigInteger extends ConfigMetaBasicType
  case object TBigInt extends ConfigMetaBasicType
  case object TBigDecimal extends ConfigMetaBasicType
  case object TJavaBigDecimal extends ConfigMetaBasicType
  case object TConfig extends ConfigMetaBasicType
  case object TConfigObject extends ConfigMetaBasicType
  case object TConfigValue extends ConfigMetaBasicType
  case object TConfigList extends ConfigMetaBasicType
  case object TConfigMemorySize extends ConfigMetaBasicType
}
