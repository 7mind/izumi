package com.github.pshirshov.izumi.idealingua.model.typespace

import com.github.pshirshov.izumi.idealingua.model.common.TypeId._
import com.github.pshirshov.izumi.idealingua.model.common._
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.TypeDef._
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed._
import com.github.pshirshov.izumi.idealingua.model.typespace.structures.{ConverterDef, PlainStruct, Struct}


trait Typespace {
  def domain: DomainDefinition

  def apply(id: TypeId): TypeDef

  def apply(id: ServiceId): Service

  def implementors(id: InterfaceId): List[ConverterDef]

  def parentsInherited(id: TypeId): List[InterfaceId]

  def parentsStructural(id: TypeId): List[InterfaceId]

  def enumFields(id: StructureId): Struct

  def structure(defn: IdentifierId): PlainStruct

  def structure(defn: Identifier): PlainStruct

  def structure(defn: WithStructure): Struct

  def toDtoName(id: TypeId): String

  def sameSignature(tid: StructureId): List[DTO]

  def compatibleImplementors(id: InterfaceId): List[ConverterDef]

  protected[typespace] def types: TypeCollection

}





