package com.github.pshirshov.izumi.idealingua.model.il.ast.raw

import com.github.pshirshov.izumi.idealingua.model.common.{AbstractIndefiniteId, TypeName}

final case class RawAdtMember(typeId: AbstractIndefiniteId, memberName: Option[TypeName], meta: RawNodeMeta) extends RawWithMeta {
  override def updateMeta(f: RawNodeMeta => RawNodeMeta): RawAdtMember = this.copy(meta = f(meta))
}
