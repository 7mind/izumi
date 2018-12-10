package com.github.pshirshov.izumi.idealingua.model.il.ast.raw

case class RawEnumMember(value: String, meta: RawNodeMeta) {
  override def toString: String = value
}
