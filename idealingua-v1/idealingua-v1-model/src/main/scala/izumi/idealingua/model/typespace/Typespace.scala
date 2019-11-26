package izumi.idealingua.model.typespace

import izumi.idealingua.model.common.TypeId._
import izumi.idealingua.model.common._
import izumi.idealingua.model.il.ast.typed.DefMethod.RPCMethod
import izumi.idealingua.model.il.ast.typed.TypeDef._
import izumi.idealingua.model.il.ast.typed._
import izumi.idealingua.model.typespace.structures.{ConverterDef, PlainStruct, Struct}


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

  def parentsInherited(id: TypeId): List[InterfaceId]

  def implementingDtos(id: InterfaceId): List[DTOId]

  protected[typespace] def compatibleDtos(id: InterfaceId): List[DTOId]
}

trait StructuralQueries {
  def conversions(id: InterfaceId): List[ConverterDef]

  def constructors(struct: Struct): List[ConverterDef]

  def structuralParents(id: Struct): List[Struct]

  def structure(id: StructureId): Struct

  def structure(defn: IdentifierId): PlainStruct

  def structure(defn: Identifier): PlainStruct

  def structure(defn: WithStructure): Struct

  def sameSignature(tid: StructureId): List[DTO]

  protected def converters(id: InterfaceId, implementors: List[StructureId]): List[ConverterDef]
}

trait TypespaceTools {
  def methodOutputSuffix: String

  def badAltBranchName: String

  def methodInputSuffix: String

  def goodAltBranchName: String

  def goodAltSuffix: String

  def badAltSuffix: String

  def idToParaName(id: TypeId): String

  def implId(id: InterfaceId): DTOId

  def sourceId(id: DTOId): Option[InterfaceId]

  def defnId(id: StructureId): InterfaceId

  def methodToOutputName(method: RPCMethod): String

  def methodToPositiveTypeName(method: RPCMethod): String

  def methodToNegativeTypeName(method: RPCMethod): String

  def toPositiveBranchName(id: AdtId): String

  def toNegativeBranchName(id: AdtId): String

  def toDtoName(id: TypeId): String

  def toInterfaceName(id: TypeId): String
}

trait TypespaceData {
  def domain: DomainDefinition

  def types: TypeCollectionData
}

trait Typespace extends TypespaceData {

  override def types: TypeCollection

  def inheritance: InheritanceQueries

  def structure: StructuralQueries

  def tools: TypespaceTools


  def resolver: TypeResolver

  def apply(id: TypeId): TypeDef

  def apply(id: ServiceId): Service

  def dealias(t: TypeId): TypeId

  protected[typespace] def transitivelyReferenced: Map[DomainId, Typespace]
}





