package com.github.pshirshov.izumi.idealingua.model.typespace

import com.github.pshirshov.izumi.idealingua.model.common.TypeId._
import com.github.pshirshov.izumi.idealingua.model.common._
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.TypeDef._
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed._
import com.github.pshirshov.izumi.idealingua.model.typespace.structures.{ConverterDef, PlainStruct, Struct}


trait Resolver {
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

trait Inheritance {
  def allParents(id: TypeId): List[InterfaceId]

  protected[typespace] def parentsInherited(id: TypeId): List[InterfaceId]

  protected[typespace] def implementingDtos(id: InterfaceId): List[DTOId]

  protected[typespace] def compatibleDtos(id: InterfaceId): List[DTOId]
}

trait Typespace {
  def inheritance: Inheritance

  def domain: DomainDefinition

  def apply(id: TypeId): TypeDef

  def apply(id: ServiceId): Service

  def implementors(id: InterfaceId): List[ConverterDef]

  def enumFields(id: StructureId): Struct

  def structure(defn: IdentifierId): PlainStruct

  def structure(defn: Identifier): PlainStruct

  def structure(defn: WithStructure): Struct

  //def toDtoName(id: TypeId): String
  def implId(id: InterfaceId): DTOId

  def sameSignature(tid: StructureId): List[DTO]

  def compatibleImplementors(id: InterfaceId): List[ConverterDef]

  def requiredInterfaces(s: Struct): List[InterfaceId]

  protected[typespace] def types: TypeCollection

}





