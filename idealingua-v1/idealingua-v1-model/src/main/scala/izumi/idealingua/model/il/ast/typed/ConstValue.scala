package izumi.idealingua.model.il.ast.typed

import izumi.idealingua.model.common.TypeId

sealed trait ConstValue

object ConstValue {

  final case class CInt(value: Int) extends ConstValue
  final case class CLong(value: Long) extends ConstValue
  final case class CFloat(value: Double) extends ConstValue
  final case class CString(value: String) extends ConstValue
  final case class CBool(value: Boolean) extends ConstValue
  final case class CMap(value: Map[String, ConstValue]) extends ConstValue
  final case class CList(value: List[ConstValue]) extends ConstValue

  sealed trait Typed extends ConstValue {
    def typeId: TypeId
  }

  final case class CTypedList(typeId: TypeId, value: CList) extends Typed
  final case class CTypedObject(typeId: TypeId, value: CMap) extends Typed
  final case class CTyped(typeId: TypeId, value: ConstValue) extends Typed

}
