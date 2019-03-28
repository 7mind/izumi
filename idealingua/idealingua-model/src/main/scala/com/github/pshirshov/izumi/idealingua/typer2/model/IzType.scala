package com.github.pshirshov.izumi.idealingua.typer2.model

import com.github.pshirshov.izumi.idealingua.model.il.ast.InputPosition
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns.RawStructure
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns.RawTypeDef.WithTemplating
import com.github.pshirshov.izumi.idealingua.typer2.model.IzTypeId.model.IzName
import com.github.pshirshov.izumi.idealingua.typer2.model.IzTypeReference.model.IzTypeArgName


sealed trait IzType {
  def id: IzTypeId
}


object IzType {
  object model {
    final case class TypedAnnoRef(name: String, ref: TypedConstId)
    final case class NodeMeta(doc: Seq[String], annos: Seq[TypedAnnoRef], pos: InputPosition)

    final case class FName(name: String)
    final case class BasicField(name: FName, ref: IzTypeReference)
    final case class FieldSource(in: IzTypeId, as: IzTypeReference, number: Int, distance: Int, meta: NodeMeta)
    final case class FullField(name: FName, tpe: IzTypeReference, defined: Seq[FieldSource]) {
      def basic: BasicField = BasicField(name, tpe)
    }

    final case class EnumMember(name: String, value: Option[TypedConstId], meta: NodeMeta)

    sealed trait AdtMember {
      def name: String
      def ref: IzTypeReference
    }
    final case class AdtMemberRef(name: String, ref: IzTypeReference, meta: NodeMeta) extends AdtMember
    final case class AdtMemberNested(name: String, ref: IzTypeReference, meta: NodeMeta) extends AdtMember
  }
  import model._

  sealed trait Generic extends IzType {
    def args: Seq[IzTypeArgName]
  }

  final case class CustomTemplate(id: IzTypeId, args: Seq[IzTypeArgName], decl: WithTemplating) extends Generic

  sealed trait WithTemplateSupport {
    this: IzType =>
  }
  sealed trait IzStructure extends IzType {
    def id: IzTypeId
    def fields: Seq[FullField]
    def parents: Seq[IzTypeId]
    def allParents: Set[IzTypeId]
    def allStructuralParents: Set[IzTypeId]
    def meta: NodeMeta
    def defn: RawStructure // we need it as a template for cloning
  }
  final case class DTO(
                        id: IzTypeId,
                        fields: Seq[FullField],
                        parents: Seq[IzTypeId],
                        allParents: Set[IzTypeId],
                        allStructuralParents: Set[IzTypeId],
                        meta: NodeMeta,
                        defn: RawStructure
                      ) extends IzStructure with WithTemplateSupport
  final case class Interface(
                              id: IzTypeId,
                              fields: Seq[FullField],
                              parents: Seq[IzTypeId],
                              allParents: Set[IzTypeId],
                              allStructuralParents: Set[IzTypeId],
                              meta: NodeMeta,
                              defn: RawStructure
                            ) extends IzStructure with WithTemplateSupport

  final case class Adt(members: Seq[AdtMember], meta: NodeMeta, contract: Interface, contractDefn: Option[RawStructure]) extends IzType with WithTemplateSupport {
    override def id: IzTypeId = contract.id
  }

  final case class IzAlias(id: IzTypeId, source: IzTypeReference, meta: NodeMeta) extends IzType
  final case class Identifier(id: IzTypeId, fields: Seq[FullField], meta: NodeMeta) extends IzType
  final case class Enum(id: IzTypeId, members: Seq[EnumMember], meta: NodeMeta) extends IzType

  sealed trait Foreign extends IzType
  final case class ForeignScalar(id: IzTypeId, mapping: Map[String, String], meta: NodeMeta) extends Foreign
  final case class Interpolation(parts: Seq[String], parameters: Seq[IzTypeArgName])
  final case class ForeignGeneric(id: IzTypeId, args: Seq[IzTypeArgName], mapping: Map[String, Interpolation], meta: NodeMeta) extends Generic with Foreign

  sealed trait TargetInterface extends IzType {
    def id: IzTypeId
    def methods: List[IzMethod]
    def meta: NodeMeta
  }
  final case class Buzzer(id: IzTypeId, methods: List[IzMethod], meta: NodeMeta) extends TargetInterface
  final case class Service(id: IzTypeId, methods: List[IzMethod], meta: NodeMeta) extends TargetInterface

  // builtins
  sealed trait BuiltinType extends IzType {
    def names: Seq[IzName]
    def id: IzTypeId.BuiltinTypeId
  }

  sealed class BuiltinScalar(val names: IzName*) extends BuiltinType {
    def id: IzTypeId.BuiltinTypeId = IzTypeId.BuiltinTypeId(names.head)
  }

  object BuiltinScalar {
    import IzNameTools._
    final case object TBool extends BuiltinScalar("bit", "bool", "boolean")
    final case object TString extends BuiltinScalar("str", "string")
    final case object TInt8 extends BuiltinScalar("i08", "byte", "int8")
    final case object TInt16 extends BuiltinScalar("i16", "short", "int16")
    final case object TInt32 extends BuiltinScalar("i32", "int", "int32")
    final case object TInt64 extends BuiltinScalar("i64", "long", "int64")
    final case object TUInt8 extends BuiltinScalar("u08", "ubyte", "uint8")
    final case object TUInt16 extends BuiltinScalar("u16", "ushort", "uint16")
    final case object TUInt32 extends BuiltinScalar("u32", "uint", "uint32")
    final case object TUInt64 extends BuiltinScalar("u64", "ulong", "uint64")
    final case object TFloat extends BuiltinScalar("f32", "flt", "float")
    final case object TDouble extends BuiltinScalar("f64", "dbl", "double")
    final case object TUUID extends BuiltinScalar("uid", "uuid")
    final case object TBLOB extends BuiltinScalar("blob", "blb", "bytes")
    final case object TTs extends BuiltinScalar("tsl", "datetimel", "dtl")
    final case object TTsTz extends BuiltinScalar("tsz", "datetimez", "dtz")
    final case object TTsU extends BuiltinScalar("tsu", "datetimeu", "dtu")
    final case object TTime extends BuiltinScalar("time")
    final case object TDate extends BuiltinScalar("date")
    final case object TErr extends BuiltinScalar("err", "error")
    final case object TAny extends BuiltinScalar("any")
  }

  sealed class BuiltinGeneric(val args: Seq[IzTypeArgName], val names: IzName*) extends Generic with BuiltinType {
    def id: IzTypeId.BuiltinTypeId = IzTypeId.BuiltinTypeId(names.head)
  }

  object BuiltinGeneric {
    import IzNameTools._
    final case object TList extends BuiltinGeneric(Seq(IzTypeArgName("T")), "lst", "list")
    final case object TSet extends BuiltinGeneric(Seq(IzTypeArgName("T")), "set")
    final case object TOption extends BuiltinGeneric(Seq(IzTypeArgName("T")), "opt", "option")
    final case object TMap extends BuiltinGeneric(Seq(IzTypeArgName("K"), IzTypeArgName("V")), "map", "dict")
    final case object TEither extends BuiltinGeneric(Seq(IzTypeArgName("L"), IzTypeArgName("R")), "either")
  }

}




