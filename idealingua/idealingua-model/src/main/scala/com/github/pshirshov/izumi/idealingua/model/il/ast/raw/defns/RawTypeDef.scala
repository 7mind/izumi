package com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns

import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns.RawAdt.Member
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.typeid.{RawDeclaredTypeName, RawNongenericRef, RawRef, TemplateDecl}


sealed trait RawTypeDef


case class InterpContext(parts: Seq[String], parameters: Seq[String])

object RawTypeDef {

  sealed trait BasicTypeDecl extends RawTypeDef {
    def id: RawDeclaredTypeName
    def meta: RawNodeMeta
  }


  final case class Interface(id: RawDeclaredTypeName, struct: RawStructure, meta: RawNodeMeta) extends BasicTypeDecl

  final case class DTO(id: RawDeclaredTypeName, struct: RawStructure, meta: RawNodeMeta) extends BasicTypeDecl

  final case class Enumeration(id: RawDeclaredTypeName, struct: RawEnum, meta: RawNodeMeta) extends BasicTypeDecl

  final case class Alias(id: RawDeclaredTypeName, target: RawRef, meta: RawNodeMeta) extends BasicTypeDecl

  final case class Identifier(id: RawDeclaredTypeName, fields: RawTuple, meta: RawNodeMeta) extends BasicTypeDecl

  final case class Adt(id: RawDeclaredTypeName, alternatives: List[Member], meta: RawNodeMeta) extends BasicTypeDecl

  final case class NewType(id: RawDeclaredTypeName, source: RawNongenericRef, modifiers: Option[RawStructure], meta: RawNodeMeta) extends RawTypeDef

  final case class DeclaredType(id: RawDeclaredTypeName, meta: RawNodeMeta) extends RawTypeDef

  final case class ForeignType(id: TemplateDecl, mapping: Map[String, InterpContext], meta: RawNodeMeta) extends RawTypeDef

}



