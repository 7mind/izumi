package izumi.distage.config.codec

import izumi.fundamentals.platform.language.{CodePosition, CodePositionMaterializer}
import izumi.fundamentals.platform.strings.IzString.*

final case class ConfigMetaTypeId(owner: Option[String], short: String, typeArguments: Seq[ConfigMetaTypeId]) {
  override def toString: String = {
    val parts = Seq(owner.getOrElse("_"), short) ++ Option(typeArguments.mkString("[", ",", "]")).toSeq.filterNot(_ => typeArguments.isEmpty)
    parts.mkString(".")
  }
}

sealed trait ConfigMetaType {
  def id: ConfigMetaTypeId
}
object ConfigMetaType {
  final case class TCaseClass(id: ConfigMetaTypeId, fields: Seq[(String, ConfigMetaType)]) extends ConfigMetaType {
    override def toString: String = s"Class $id with fields ${fields.map { case (n, v) => s"$n: $v" }.niceList().shift(2)}"
  }
  final case class TSealedTrait(id: ConfigMetaTypeId, branches: Set[(String, ConfigMetaType)]) extends ConfigMetaType {
    override def toString: String = s"ADT $id with branches ${branches.map { case (n, v) => s"$n: $v" }.niceList().shift(2)}"
  }
  final case class TBasic(tpe: ConfigMetaBasicType) extends ConfigMetaType {
    override def id: ConfigMetaTypeId = ConfigMetaTypeId(None, tpe.toString, Seq.empty)
  }
  final case class TList(tpe: ConfigMetaType) extends ConfigMetaType {
    override def id: ConfigMetaTypeId = ConfigMetaTypeId(None, "List", Seq(tpe.id))
  }
  final case class TSet(tpe: ConfigMetaType) extends ConfigMetaType {
    override def id: ConfigMetaTypeId = ConfigMetaTypeId(None, "Set", Seq(tpe.id))
  }
  final case class TOption(tpe: ConfigMetaType) extends ConfigMetaType {
    override def id: ConfigMetaTypeId = ConfigMetaTypeId(None, "Option", Seq(tpe.id))
  }
  final case class TMap(keyType: ConfigMetaType, valueType: ConfigMetaType) extends ConfigMetaType {
    override def id: ConfigMetaTypeId = ConfigMetaTypeId(None, "Map", Seq(keyType.id, valueType.id))
  }
  final case class TUnknown(pos: CodePosition) extends ConfigMetaType {
    override def id: ConfigMetaTypeId = ConfigMetaTypeId(None, "???", Seq.empty)
  }
  object TUnknown {
    def apply()(implicit ev: CodePositionMaterializer, dummyImplicit: DummyImplicit): TUnknown = new TUnknown(ev.get)
  }

  final case class TVariant(id: ConfigMetaTypeId, variants: Set[ConfigMetaType]) extends ConfigMetaType {
    override def toString: String = s"Choice $id with variants ${variants.niceList().shift(2)}"
  }
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
