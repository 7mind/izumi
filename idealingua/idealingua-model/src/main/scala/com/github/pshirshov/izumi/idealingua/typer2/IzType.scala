package com.github.pshirshov.izumi.idealingua.typer2

import com.github.pshirshov.izumi.idealingua.model.il.ast.InputPosition
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns.RawStructure
import com.github.pshirshov.izumi.idealingua.typer2.IzTypeId.{IzName, IzNamespace, IzPackage}
import com.github.pshirshov.izumi.idealingua.typer2.IzTypeReference.IzTypeArgName

import scala.language.implicitConversions

sealed trait TypePrefix

object TypePrefix {
  case class UserTLT(location: IzPackage) extends TypePrefix
  case class UserT(location: IzPackage, subpath: Seq[IzNamespace]) extends TypePrefix
}

sealed trait IzTypeId {
  import IzTypeId._
  def name: IzName
}

object IzTypeId {
  final case class BuiltinType(name: IzName) extends IzTypeId
  final case class UserType(prefix: TypePrefix, name: IzName) extends IzTypeId


  // impl
  case class IzDomainPath(name: String)
  case class IzNamespace(name: String)

  case class IzName(name: String)

  case class IzPackage(path: Seq[IzDomainPath])
}

sealed trait IzType {
  def id: IzTypeId
}

object IzNameTools {
  implicit def convert(name: String): IzName  = IzName(name)
}

object IzType {
  sealed trait Generic extends IzType {
    def args: Seq[IzTypeArgName]
  }

  sealed trait BuiltinType extends IzType {
    def names: Seq[IzName]
    def id: IzTypeId.BuiltinType
  }



  sealed class BuiltinGeneric(val args: Seq[IzTypeArgName], val names: IzName*) extends Generic with BuiltinType {
    def id: IzTypeId.BuiltinType = IzTypeId.BuiltinType(names.head)
  }


  sealed class BuiltinScalar(val names: IzName*) extends BuiltinType {
     def id: IzTypeId.BuiltinType = IzTypeId.BuiltinType(names.head)
   }

  object BuiltinScalar {
    import IzNameTools._
    final case object TBool extends BuiltinScalar("bit", "bool", "boolean")
    final case object TString extends BuiltinScalar("str", "string")
    final case object TInt extends BuiltinScalar("i32", "int", "int32")
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
  }

  object BuiltinGeneric {
    import IzNameTools._
    final case object TList extends BuiltinGeneric(Seq(IzTypeArgName("T")), "lst", "list")
    final case object TSet extends BuiltinGeneric(Seq(IzTypeArgName("T")), "set")
    final case object TOption extends BuiltinGeneric(Seq(IzTypeArgName("T")), "opt", "option")
    final case object TMap extends BuiltinGeneric(Seq(IzTypeArgName("K"), IzTypeArgName("V")), "map", "dict")
  }

//  case object Dummy extends IzType
//
//  case class Scalar(id: IzTypeId) extends IzType
//
//
//  case class AppliedGeneric(id: IzTypeId, args: Seq[IzTypeArg]) extends IzType
//
//  case class PartiallyAppliedGeneric(id: IzTypeId, args: Seq[IzTypeArg], free: Seq[IzTypeArgName]) extends IzType
//
//  // impl
  case class NodeMeta(doc: Option[String], annos: Seq[Nothing], pos: InputPosition)

  case class FName(name: String)

  case class Field2(name: FName, tpe: IzTypeReference, defined: Seq[IzTypeId], meta: NodeMeta) {
    def basic: Basic = Basic(name, tpe)
  }
  case class Basic(name: FName, ref: IzTypeReference)

  case class IzAlias(id: IzTypeId, source: IzTypeId, meta: NodeMeta) extends IzType

  sealed trait IzStructure extends IzType {
    def id: IzTypeId
    def fields: Seq[Field2]
    def parents: Seq[IzTypeId]
    def meta: NodeMeta
    def defn: RawStructure
  }
  case class DTO(id: IzTypeId, fields: Seq[Field2], parents: Seq[IzTypeId], meta: NodeMeta, defn: RawStructure) extends IzStructure
  case class Interface(id: IzTypeId, fields: Seq[Field2], parents: Seq[IzTypeId], meta: NodeMeta, defn: RawStructure) extends IzStructure

  case class Identifier(id: IzTypeId, fields: Seq[Field2], meta: NodeMeta) extends IzType

  case class EnumMember(name: String, value: Option[Nothing], meta: NodeMeta)
  case class Enum(id: IzTypeId, members: Seq[EnumMember], meta: NodeMeta) extends IzType

  case class TODO(id: IzTypeId) extends IzType
}





