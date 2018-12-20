package com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns

case class RawEnumMember(value: String, associated: Option[RawVal], meta: RawNodeMeta) {
  override def toString: String = value
}
