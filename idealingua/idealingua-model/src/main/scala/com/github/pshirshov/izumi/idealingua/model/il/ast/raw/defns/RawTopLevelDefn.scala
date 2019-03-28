package com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns


sealed trait RawTopLevelDefn


object RawTopLevelDefn {


  sealed trait NamedDefn extends RawTopLevelDefn

  sealed trait TypeDefn extends NamedDefn {
    def defn: RawTypeDef
  }

  final case class TLDBaseType(defn: RawTypeDef.BasicTypeDecl) extends TypeDefn

  final case class TLDNewtype(defn: RawTypeDef.NewType) extends TypeDefn

  final case class TLDForeignType(defn: RawTypeDef.ForeignType) extends TypeDefn

  final case class TLDTemplate(defn: RawTypeDef.Template) extends TypeDefn

  final case class TLDInstance(defn: RawTypeDef.Instance) extends TypeDefn

  final case class TLDConsts(v: RawConstBlock) extends RawTopLevelDefn

  final case class TLDDeclared(v: NamedDefn) extends RawTopLevelDefn
}
