package izumi.idealingua.model.il.ast.raw.models

import izumi.idealingua.model.il.ast.raw.defns.RawTopLevelDefn

sealed trait ModelMember

object ModelMember {
  final case class MMTopLevelDefn(v: RawTopLevelDefn) extends ModelMember
  final case class MMInclusion(v: Inclusion) extends ModelMember

}
