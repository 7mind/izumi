package com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns

import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.typeid.RawRef

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

  final case class CTyped(typeId: RawRef, value: RawVal) extends RawVal

  final case class CTypedList(typeId: RawRef, value: List[RawVal]) extends RawVal

  final case class CTypedObject(typeId: RawRef, value: Map[String, RawVal]) extends RawVal

}
