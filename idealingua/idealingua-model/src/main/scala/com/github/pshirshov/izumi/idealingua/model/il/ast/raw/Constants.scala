package com.github.pshirshov.izumi.idealingua.model.il.ast.raw

import com.github.pshirshov.izumi.idealingua.model.common.AbstractIndefiniteId
import com.github.pshirshov.izumi.idealingua.model.common.TypeId.ConstId


trait RawVal[+T] {
  def value: T
}

object RawVal {
  final case class CInt(value: Int) extends RawVal[Int]
  final case class CLong(value: Long) extends RawVal[Long]
  final case class CFloat(value: Double) extends RawVal[Double]
  final case class CString(value: String) extends RawVal[String]
  final case class CBool(value: Boolean) extends RawVal[Boolean]

  final case class CTyped(typeId: AbstractIndefiniteId, value: Any) extends RawVal[Any]

  final case class CTypedObject(typeId: AbstractIndefiniteId, value: Map[String, RawVal[Any]]) extends RawVal[Map[String, RawVal[Any]]]
  final case class CMap(value: Map[String, RawVal[Any]]) extends RawVal[Map[String, RawVal[Any]]]

  final case class CTypedList(typeId: AbstractIndefiniteId, value: List[RawVal[Any]]) extends RawVal[List[RawVal[Any]]]
  final case class CList(value: List[RawVal[Any]]) extends RawVal[List[RawVal[Any]]]

}

final case class RawConst(id: ConstId, const: RawVal[Any], doc: Option[String])

final case class Constants(consts: List[RawConst])
