package izumi.idealingua.model.il.ast.raw.defns

import izumi.idealingua.model.common.{AbstractIndefiniteId, TypeName}

final case class RawAdt(alternatives: List[RawAdt.Member])

object RawAdt {

  sealed trait Member

  object Member {

    final case class TypeRef(typeId: AbstractIndefiniteId, memberName: Option[TypeName], meta: RawNodeMeta) extends Member

    final case class NestedDefn(nested: RawTypeDef.WithId) extends Member

  }

}
