package com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns

import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns.RawAdt.Member
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.typeid._


sealed trait RawTypeDef


case class InterpContext(parts: Seq[String], parameters: Seq[String])

object RawTypeDef {

  sealed trait BasicTypeDecl extends RawTypeDef {
    def id: RawDeclaredTypeName

    def meta: RawNodeMeta
  }

  sealed trait WithTemplating extends BasicTypeDecl

  final case class Interface(id: RawDeclaredTypeName, struct: RawStructure, meta: RawNodeMeta) extends WithTemplating

  final case class DTO(id: RawDeclaredTypeName, struct: RawStructure, meta: RawNodeMeta) extends WithTemplating

  final case class Adt(id: RawDeclaredTypeName, alternatives: List[Member], meta: RawNodeMeta) extends WithTemplating

  final case class Enumeration(id: RawDeclaredTypeName, struct: RawEnum, meta: RawNodeMeta) extends BasicTypeDecl

  final case class Alias(id: RawDeclaredTypeName, target: RawRef, meta: RawNodeMeta) extends BasicTypeDecl

  final case class Identifier(id: RawDeclaredTypeName, fields: RawTuple, meta: RawNodeMeta) extends BasicTypeDecl

  final case class NewType(id: RawDeclaredTypeName, source: RawTypeNameRef, modifiers: Option[RawStructure], meta: RawNodeMeta) extends RawTypeDef

  final case class DeclaredType(id: RawDeclaredTypeName, meta: RawNodeMeta) extends RawTypeDef

  final case class ForeignType(id: TemplateDecl, mapping: Map[String, InterpContext], meta: RawNodeMeta) extends RawTypeDef

  final case class Template(arguments: List[RawTemplateNoArg], decl: WithTemplating, meta: RawNodeMeta) extends RawTypeDef

  final case class Instance(id: RawDeclaredTypeName, source: RawRef, meta: RawNodeMeta) extends RawTypeDef

}



