package com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns

import com.github.pshirshov.izumi.idealingua.model.common.ConstId
import com.github.pshirshov.izumi.idealingua.model.il.ast.InputPosition

final case class RawConstBlock(name: String, consts: List[RawConst])

final case class RawConstMeta(doc: Option[String], position: InputPosition)

object RawConstMeta {
  def apply(position: InputPosition): RawConstMeta = new RawConstMeta(None, position)
}

final case class RawConst(id: ConstId, const: RawVal, meta: RawConstMeta)

