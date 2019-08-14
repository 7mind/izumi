package izumi.idealingua.model.il.ast.raw.defns

import izumi.idealingua.model.common.AbstractIndefiniteId

sealed trait RawVal

object RawVal {

  sealed trait RawValScalar extends RawVal

  final case class CInt(value: Int) extends RawValScalar

  final case class CLong(value: Long) extends RawValScalar

  final case class CFloat(value: Double) extends RawValScalar

  final case class CString(value: String) extends RawValScalar

  final case class CBool(value: Boolean) extends RawValScalar

  final case class CMap(value: Map[String, RawVal]) extends RawVal

  final case class CList(value: List[RawVal]) extends RawVal

  final case class CTyped(typeId: AbstractIndefiniteId, value: RawVal) extends RawVal

  final case class CTypedList(typeId: AbstractIndefiniteId, value: List[RawVal]) extends RawVal

  final case class CTypedObject(typeId: AbstractIndefiniteId, value: Map[String, RawVal]) extends RawVal

}
