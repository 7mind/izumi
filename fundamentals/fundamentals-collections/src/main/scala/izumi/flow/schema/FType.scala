package izumi.flow.schema

import izumi.flow.schema.FType.FRecord

sealed trait FType {}

object FType {
  sealed trait FBuiltin extends FType
  case object FString extends FBuiltin
  case object FInt extends FBuiltin
  case object FLong extends FBuiltin
  case object FDouble extends FBuiltin

  case class FField(name: String, tpe: FBuiltin)
  case class FRecord(name: String, fields: List[FField]) extends FType
}

sealed trait FValue {
  def tpe: FType
  def value: Any
  def valueRef: AnyRef = value.asInstanceOf[AnyRef]
}

object FValue {
  sealed trait FVBuiltin extends FValue { def tpe: FType.FBuiltin }
  case class FVString(value: String) extends FVBuiltin { val tpe: FType.FBuiltin = FType.FString }
  case class FVInt(value: Int) extends FVBuiltin { val tpe: FType.FBuiltin = FType.FDouble }
  case class FVLong(value: Long) extends FVBuiltin { val tpe: FType.FBuiltin = FType.FLong }
  case class FVDouble(value: Double) extends FVBuiltin { val tpe: FType.FBuiltin = FType.FDouble }

  case class FVField(name: String, value: FVBuiltin) {
    def tpe: FType.FField = FType.FField(name, value.tpe)
  }
  case class FVRecord(name: String, fields: List[FVField], tpe: FRecord) extends FValue {
    override def value: Any = List(fields.map(_.value.value))
  }

  //{ val tpe: FType = FType.FRecord(fields.map(_.tpe)) } {

}
