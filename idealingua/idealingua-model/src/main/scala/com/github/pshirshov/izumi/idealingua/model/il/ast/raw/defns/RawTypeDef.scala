package com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns

import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns.RawAdt.Member
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns.RawTypeDef.WithTemplating
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.typeid._


sealed trait RawTypeDef {
  def meta: RawNodeMeta

}


case class InterpContext(parts: Seq[String], parameters: Seq[String])

object RawTypeDef {

  sealed trait BasicTypeDecl extends RawTypeDef {
    def id: RawDeclaredTypeName
  }

  sealed trait WithTemplating extends BasicTypeDecl

  sealed trait TargetInterface extends WithTemplating

  final case class Interface(id: RawDeclaredTypeName, struct: RawStructure, meta: RawNodeMeta) extends WithTemplating

  final case class DTO(id: RawDeclaredTypeName, struct: RawStructure, meta: RawNodeMeta) extends WithTemplating

  final case class Adt(id: RawDeclaredTypeName, contract: Option[RawStructure], alternatives: List[Member], meta: RawNodeMeta) extends WithTemplating

  final case class Enumeration(id: RawDeclaredTypeName, struct: RawEnum, meta: RawNodeMeta) extends BasicTypeDecl

  final case class Alias(id: RawDeclaredTypeName, target: RawRef, meta: RawNodeMeta) extends BasicTypeDecl

  final case class Identifier(id: RawDeclaredTypeName, fields: RawTuple, meta: RawNodeMeta) extends BasicTypeDecl

  final case class NewType(id: RawDeclaredTypeName, source: RawTypeNameRef, modifiers: Option[RawClone], meta: RawNodeMeta) extends RawTypeDef

  final case class ForeignType(id: TemplateDecl, mapping: Map[String, InterpContext], meta: RawNodeMeta) extends RawTypeDef

  final case class Template(arguments: List[RawTemplateNoArg], decl: WithTemplating, meta: RawNodeMeta) extends RawTypeDef

  final case class Instance(id: RawDeclaredTypeName, source: RawRef, meta: RawNodeMeta) extends RawTypeDef

  final case class RawBuzzer(id: RawDeclaredTypeName, events: List[RawMethod], meta: RawNodeMeta) extends TargetInterface

  final case class RawService(id: RawDeclaredTypeName, methods: List[RawMethod], meta: RawNodeMeta) extends TargetInterface

}



