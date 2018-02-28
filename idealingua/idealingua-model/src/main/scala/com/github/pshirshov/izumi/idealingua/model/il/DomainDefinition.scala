package com.github.pshirshov.izumi.idealingua.model.il

import com.github.pshirshov.izumi.idealingua.model.common
import com.github.pshirshov.izumi.idealingua.model.common.{Builtin, TypeId, UserType}
import com.github.pshirshov.izumi.idealingua.model.common.TypeId._
import com.github.pshirshov.izumi.idealingua.model.exceptions.IDLException
import com.github.pshirshov.izumi.idealingua.model.il.DefMethod.RPCMethod
import com.github.pshirshov.izumi.idealingua.model.il.FinalDefinition._

case class DomainDefinition(
                             id: DomainId
                             , types: Seq[FinalDefinition]
                             , services: Seq[Service]
                             , referenced: Map[DomainId, DomainDefinition]
                           )

object DomainDefinition {

  def normalizeTypeIds(defn: DomainDefinition): DomainDefinition = {
    val mappedTypes = defn.types.map(fixType(defn.id))
    val mappedServices = defn.services.map(fixService(defn.id))

    defn.copy(types = mappedTypes, services = mappedServices)
  }


  def fixType(id: DomainId)(defn: FinalDefinition): FinalDefinition = {
    defn match {
      case d: Enumeration =>
        d.copy(id = fixId(id, d.id))

      case d: Alias =>
        d.copy(id = fixId(id, d.id), target = fixId(id, d.target))

      case d: Identifier =>
        d.copy(id = fixId(id, d.id), fields = fixFields(id, d.fields))

      case d: Interface =>
        d.copy(id = fixId(id, d.id), fields = fixFields(id, d.fields), interfaces = fixIds(id, d.interfaces), concepts = fixIds(id, d.concepts) )

      case d: DTO =>
        d.copy(id = fixId(id, d.id), interfaces = fixIds(id, d.interfaces))

      case d: Adt =>
        d.copy(id = fixId(id, d.id), alternatives = fixIds(id, d.alternatives))
    }
  }

  def fixService(id: DomainId)(defn: Service): Service = {
    defn.copy(id = fixId(id, defn.id), methods = defn.methods.map(fixMethod(id, _)))
  }

  private def fixIds[T <: TypeId](id: DomainId, d: List[T]): List[T] = {
    d.map(fixId(id, _))
  }

  def fixFields(id: DomainId, fields: Aggregate): Aggregate = {
    fields.map(f => f.copy(typeId = fixId(id, f.typeId)))
  }

  def fixSignature(id: DomainId, signature: DefMethod.Signature): DefMethod.Signature = {
    signature.copy(input = fixIds(id, signature.input), output = fixIds(id, signature.output))
  }

  def fixMethod(id: DomainId, method: DefMethod): DefMethod = {
    method match {
      case m: RPCMethod =>
        m.copy(signature = fixSignature(id, m.signature))
    }
  }

  def fixPkg(domainId: DomainId, pkg: common.Package): common.Package = {
    if (pkg.isEmpty) {
      domainId.toPackage
    } else {
      pkg
    }
  }

  def fixId[T <: TypeId](domainId: DomainId, t: T): T = {
    (t match {
      case t: DTOId =>
        t.copy(pkg = fixPkg(domainId, t.pkg))

      case t: InterfaceId =>
        t.copy(pkg = fixPkg(domainId, t.pkg))

      case t: AdtId =>
        t.copy(pkg = fixPkg(domainId, t.pkg))

      case t: AliasId =>
        t.copy(pkg = fixPkg(domainId, t.pkg))

      case t: EnumId =>
        t.copy(pkg = fixPkg(domainId, t.pkg))

      case t: IdentifierId =>
        t.copy(pkg = fixPkg(domainId, t.pkg))

      case t: ServiceId =>
        t.copy(pkg = fixPkg(domainId, t.pkg))

      case t: UserType =>
        t.copy(pkg = fixPkg(domainId, t.pkg))

      case t: Builtin =>
        t

      case _ =>
        throw new IDLException(s"Unsupported: $t")
    }).asInstanceOf[T]
  }


}
