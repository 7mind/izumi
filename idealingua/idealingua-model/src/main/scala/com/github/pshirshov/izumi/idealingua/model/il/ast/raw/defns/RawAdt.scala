package com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns

import com.github.pshirshov.izumi.idealingua.model.common.TypeName
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.typeid.RawRef

final case class RawAdt(alternatives: List[RawAdt.Member])

object RawAdt {

  sealed trait Member

  object Member {

    final case class TypeRef(typeId: RawRef, memberName: Option[TypeName], meta: RawNodeMeta) extends Member

    final case class NestedDefn(nested: RawTypeDef.BasicTypeDecl) extends Member

  }

}
