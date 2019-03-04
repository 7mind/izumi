package com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns


sealed trait RawTopLevelDefn


object RawTopLevelDefn {


  sealed trait NamedDefn extends RawTopLevelDefn

  sealed trait TypeDefn extends NamedDefn

  final case class TLDBaseType(v: RawTypeDef.BasicTypeDecl) extends TypeDefn

  final case class TLDNewtype(v: RawTypeDef.NewType) extends TypeDefn

  final case class TLDDeclared(v: RawTypeDef.DeclaredType) extends TypeDefn

  final case class TLDForeignType(v: RawTypeDef.ForeignType) extends TypeDefn

  final case class TLDTemplate(v: RawTypeDef.Template) extends TypeDefn

  final case class TLDInstance(v: RawTypeDef.Instance) extends TypeDefn

  final case class TLDService(v: RawService) extends NamedDefn

  final case class TLDBuzzer(v: RawBuzzer) extends NamedDefn

  // not supported by cogen yet
  final case class TLDStreams(v: RawStreams) extends NamedDefn

  final case class TLDConsts(v: RawConstBlock) extends RawTopLevelDefn

}
