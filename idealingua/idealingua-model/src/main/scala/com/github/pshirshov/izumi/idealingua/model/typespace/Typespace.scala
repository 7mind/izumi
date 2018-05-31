package com.github.pshirshov.izumi.idealingua.model.typespace

import com.github.pshirshov.izumi.idealingua.model.common.TypeId._
import com.github.pshirshov.izumi.idealingua.model.common._
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.TypeDef._
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed._
import com.github.pshirshov.izumi.idealingua.model.typespace.structures.{ConverterDef, PlainStruct, Struct}


trait TypeResolver {
  def apply(id: ServiceId): Service

  def apply(id: TypeId): TypeDef

  protected[typespace] def get(id: InterfaceId): Interface = {
    apply(id: TypeId).asInstanceOf[Interface]
  }

  protected[typespace] def get(id: StructureId): WithStructure = {
    apply(id: TypeId).asInstanceOf[WithStructure]
  }


  protected[typespace] def get(id: IdentifierId): Identifier = {
    apply(id: TypeId).asInstanceOf[Identifier]
  }

  protected[typespace] def get(id: DTOId): DTO = {
    apply(id: TypeId).asInstanceOf[DTO]
  }

}

trait InheritanceQueries {
  def allParents(id: TypeId): List[InterfaceId]

  def implementingDtos(id: InterfaceId): List[DTOId]

  protected[typespace] def parentsInherited(id: TypeId): List[InterfaceId]

  protected[typespace] def compatibleDtos(id: InterfaceId): List[DTOId]
}

trait StructuralQueries {
  def conversions(id: InterfaceId): List[ConverterDef]

  def constructors(struct: Struct): List[ConverterDef]

  def structuralParents(id: Interface): List[Struct]

  def structure(id: StructureId): Struct

  def structure(defn: IdentifierId): PlainStruct

  def structure(defn: Identifier): PlainStruct

  def structure(defn: WithStructure): Struct

  def sameSignature(tid: StructureId): List[DTO]

  protected[typespace] def converters(implementors: List[StructureId], id: InterfaceId): List[ConverterDef]
}

trait TypespaceTools {
  def idToParaName(id: TypeId): String

  def implId(id: InterfaceId): DTOId

  def defnId(id: StructureId): InterfaceId

  def mkConverter(innerFields: List[SigParam], outerFields: List[SigParam], targetId: StructureId): ConverterDef
}

trait Typespace {
  def inheritance: InheritanceQueries

  def structure: StructuralQueries

  def tools: TypespaceTools

  def domain: DomainDefinition

  def apply(id: TypeId): TypeDef

  def apply(id: ServiceId): Service

  def dealias(t: TypeId): TypeId

  def implId(id: InterfaceId): DTOId

  def defnId(id: StructureId): InterfaceId

  def types: TypeCollection

  protected[typespace] def referenced: Map[DomainId, Typespace]
}





