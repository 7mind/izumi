package izumi.idealingua.model.il.ast.raw.defns

import izumi.idealingua.model.common.TypeId

case class RawEnumMember(value: String, associated: Option[RawVal], meta: RawNodeMeta) {
  override def toString: String = value
}

case class RawEnum(parents: List[TypeId.EnumId], members: List[RawEnumMember], removed: List[String])

object RawEnum {
  sealed trait EnumOp

  object EnumOp {

    final case class Extend(tpe: TypeId.EnumId) extends EnumOp

    final case class AddMember(field: RawEnumMember) extends EnumOp

    final case class RemoveMember(field: String) extends EnumOp

  }

  final case class Aux(structure: RawEnum)

  object Aux {
    def apply(v: Seq[EnumOp]): Aux = {
      import EnumOp._
      Aux(RawEnum(
        v.collect { case Extend(i) => i }.toList
        , v.collect { case AddMember(i) => i }.toList
        , v.collect { case RemoveMember(i) => i }.toList
      ))
    }
  }
}
