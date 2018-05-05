package com.github.pshirshov.izumi.idealingua.model.il.ast.typed

import com.github.pshirshov.izumi.idealingua.model.common.{TypeId, TypeName}

final case class AdtMember(typeId: TypeId, memberName: Option[TypeName]) {
  def name: TypeName = memberName.getOrElse(typeId.name).capitalize
}
