package com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns

import com.github.pshirshov.izumi.idealingua.model.common.TypeId.ConstId
import com.github.pshirshov.izumi.idealingua.model.il.ast.InputPosition

final case class RawConstBlock(consts: List[RawConst])

final case class RawConstMeta(doc: Option[String], position: InputPosition)

object RawConstMeta {
  def apply(position: InputPosition): RawConstMeta = new RawConstMeta(None, position)
}

final case class RawConst(id: ConstId, const: RawVal, meta: RawConstMeta)

