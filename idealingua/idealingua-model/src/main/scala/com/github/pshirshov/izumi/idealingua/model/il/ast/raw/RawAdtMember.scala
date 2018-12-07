package com.github.pshirshov.izumi.idealingua.model.il.ast.raw

import com.github.pshirshov.izumi.idealingua.model.common.{AbstractIndefiniteId, TypeName}

sealed trait RawAdtMember

object RawAdtMember {
  final case class RawAdtMemberRef(typeId: AbstractIndefiniteId, memberName: Option[TypeName], meta: RawNodeMeta) extends RawAdtMember
  final case class RawNestedAdtMember(nested: IdentifiedRawTypeDef) extends RawAdtMember

}
