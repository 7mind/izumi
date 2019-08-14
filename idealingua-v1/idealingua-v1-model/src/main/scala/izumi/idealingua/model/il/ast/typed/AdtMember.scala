package izumi.idealingua.model.il.ast.typed

import izumi.idealingua.model.common.{TypeId, TypeName}

final case class AdtMember(typeId: TypeId, memberName: Option[TypeName], meta: NodeMeta) {
  def wireId: TypeName = memberName.getOrElse(typeId.name)
  def typename: TypeName = wireId.capitalize
}
