package com.github.pshirshov.izumi.idealingua.model.il

import com.github.pshirshov.izumi.idealingua.model.common
import com.github.pshirshov.izumi.idealingua.model.common.TypeId._
import com.github.pshirshov.izumi.idealingua.model.common.{AbstractTypeId, Builtin, Indefinite, TypeId}
import com.github.pshirshov.izumi.idealingua.model.exceptions.IDLException


class DomainDefinitionConverter(defn: DomainDefinitionParsed) {
  final val id: DomainId = defn.id

  protected val mapping: Map[Indefinite, TypeId] = {
    (defn.types.map(_.id) ++ defn.services.map(_.id))
      .map {
        kv =>
          toIndefinite(kv) -> kv
      }
      .toMap
  }

  def makeDefinite(id: AbstractTypeId): TypeId = {
    mapping.getOrElse(toIndefinite(id), ???)
  }

  private def toIndefinite(typeId: AbstractTypeId): Indefinite = {
    Indefinite(fixId(typeId))
  }

  def convert(): DomainDefinition = {
    val mappedTypes = defn.types.map(fixType)
    val mappedServices = defn.services.map(fixService)
    val ref = defn.referenced.map(d => d._1 -> new DomainDefinitionConverter(d._2).convert())
    DomainDefinition(id = id, types = mappedTypes, services = mappedServices, referenced = ref)
  }

  def fixType(defn: ILAstParsed): ILAst = {
    defn match {
      case d: ILAstParsed.Enumeration =>
        ILAst.Enumeration(id = fixId(d.id), members = d.members)

      case d: ILAstParsed.Alias =>
        ILAst.Alias(id = fixId(d.id), target = fixId(d.target))

      case d: ILAstParsed.Identifier =>
        ILAst.Identifier(id = fixId(d.id), fields = fixFields(d.fields))

      case d: ILAstParsed.Interface =>
        ILAst.Interface(id = fixId(d.id), fields = fixFields(d.fields), interfaces = fixIds(d.interfaces), concepts = fixIds(d.concepts))

      case d: ILAstParsed.DTO =>
        ILAst.DTO(id = fixId(d.id), interfaces = fixIds(d.interfaces), concepts = fixIds(d.concepts))

      case d: ILAstParsed.Adt =>
        ILAst.Adt(id = fixId(d.id), alternatives = fixIds(d.alternatives))
    }
  }

  def fixService(defn: ILAstParsed.Service): ILAst.Service = {
    ILAst.Service(id = fixId(defn.id), methods = defn.methods.map(fixMethod))
  }

  private def fixIds[T <: AbstractTypeId, R <: TypeId](d: List[T]): List[R] = {
    d.map(fixId)
  }

  def fixFields(fields: ILAstParsed.Aggregate): ILAst.Aggregate = {
    fields.map(f => ILAst.Field(name = f.name, typeId = fixId(f.typeId)))
  }

  def fixSignature(signature: ILAstParsed.Service.DefMethod.Signature): ILAst.Service.DefMethod.Signature = {
   ILAst.Service.DefMethod.Signature(input = fixIds(signature.input), output = fixIds(signature.output))
  }

  def fixMethod(method: ILAstParsed.Service.DefMethod): ILAst.Service.DefMethod = {
    method match {
      case m: ILAstParsed.Service.DefMethod.RPCMethod =>
        ILAst.Service.DefMethod.RPCMethod(signature = fixSignature(m.signature), name = m.name)
    }
  }

  def fixPkg(domainId: DomainId, pkg: common.Package): common.Package = {
    if (pkg.isEmpty) {
      domainId.toPackage
    } else {
      pkg
    }
  }

  def fixId[T <: AbstractTypeId, R <: TypeId](t: T): R = {
    (t match {
      case t: DTOId =>
        t.copy(pkg = fixPkg(id, t.pkg))

      case t: InterfaceId =>
        t.copy(pkg = fixPkg(id, t.pkg))

      case t: AdtId =>
        t.copy(pkg = fixPkg(id, t.pkg))

      case t: AliasId =>
        t.copy(pkg = fixPkg(id, t.pkg))

      case t: EnumId =>
        t.copy(pkg = fixPkg(id, t.pkg))

      case t: IdentifierId =>
        t.copy(pkg = fixPkg(id, t.pkg))

      case t: ServiceId =>
        t.copy(pkg = fixPkg(id, t.pkg))

      case t: Indefinite =>
        makeDefinite(t)

      case t: Builtin =>
        t

      case _ =>
        throw new IDLException(s"Unsupported: $t")
    }).asInstanceOf[R]
  }


}
