package com.github.pshirshov.izumi.idealingua.model.il.ast

import com.github.pshirshov.izumi.idealingua.model.common
import com.github.pshirshov.izumi.idealingua.model.common.TypeId._
import com.github.pshirshov.izumi.idealingua.model.common._
import com.github.pshirshov.izumi.idealingua.model.exceptions.IDLException
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.{DomainDefinitionParsed, RawTypeDef}


class DomainDefinitionTyper(defn: DomainDefinitionParsed) {
  final val domainId: typed.DomainId = defn.id

  protected val mapping: Map[IndefiniteId, TypeId] = {
    defn.types.map(_.id)
      .map {
        kv =>
          toIndefinite(kv) -> fixSimpleId[AbstractTypeId, TypeId](kv)
      }
      .toMap
  }

  def convert(): typed.DomainDefinition = {
    val mappedTypes = defn.types.map(fixType)
    val mappedServices = defn.services.map(fixService)
    val ref = defn.referenced.map(d => d._1 -> new DomainDefinitionTyper(d._2).convert())
    typed.DomainDefinition(id = domainId, types = mappedTypes, services = mappedServices, referenced = ref)
  }

  protected def fixType(defn: RawTypeDef): typed.TypeDef = {
    defn match {
      case d: RawTypeDef.Enumeration =>
        typed.TypeDef.Enumeration(id = fixId(d.id): TypeId.EnumId, members = d.members)

      case d: RawTypeDef.Alias =>
        typed.TypeDef.Alias(id = fixId(d.id): TypeId.AliasId, target = fixId(d.target): TypeId)

      case d: RawTypeDef.Identifier =>
        typed.TypeDef.Identifier(id = fixId(d.id): TypeId.IdentifierId, fields = fixPrimitiveFields(d.fields))

      case d: RawTypeDef.Interface =>
        typed.TypeDef.Interface(id = fixId(d.id): TypeId.InterfaceId, struct = toStruct(d.struct))

      case d: RawTypeDef.DTO =>
        typed.TypeDef.DTO(id = fixId(d.id): TypeId.DTOId, struct = toStruct(d.struct))

      case d: RawTypeDef.Adt =>
        typed.TypeDef.Adt(id = fixId(d.id): TypeId.AdtId, alternatives = fixIds(d.alternatives))
    }
  }

  protected def toStruct(struct: raw.RawStructure): typed.Structure = {
    typed.Structure(fields = fixFields(struct.fields), removedFields = fixFields(struct.removedFields), superclasses = toSuper(struct))
  }

  protected def toSuper(struct: raw.RawStructure): typed.Super = {
    typed.Super(interfaces = fixIds(struct.interfaces), concepts = fixIds(struct.concepts), removedConcepts = fixIds(struct.removedConcepts))
  }

  protected def fixService(defn: raw.Service): typed.Service = {
    typed.Service(id = fixServiceId(defn.id), methods = defn.methods.map(fixMethod))
  }

  protected def makeDefinite(id: AbstractTypeId): TypeId = {
    downcast(id) match {
      case p: Primitive =>
        p
      case g: IndefiniteGeneric =>
        toGeneric(g)
      case v if domainId.contains(v) =>
        mapping.get(toIndefinite(v)) match {
          case Some(t) =>
            t
          case None =>
            throw new IDLException(s"Type $id is missing from domain $domainId")
        }
      case v if !domainId.contains(v) =>
        val referencedDomain = domainId.toDomainId(v)
        defn.referenced.get(referencedDomain) match {
          case Some(d) =>
            new DomainDefinitionTyper(d).makeDefinite(v)
          case None =>
            throw new IDLException(s"Domain $referencedDomain is missing from context of $domainId")
        }

    }
  }

  protected def fixId[T <: AbstractTypeId, R <: TypeId](t: T): R = {
    (t match {
      case t: IndefiniteId =>
        makeDefinite(t)

      case t: IndefiniteGeneric =>
        makeDefinite(t)

      case o =>
        fixSimpleId(o)
    }).asInstanceOf[R]
  }

  protected def fixServiceId(t: ServiceId): ServiceId = {
    t.copy(pkg = fixPkg(t.pkg))
  }

  protected def fixSimpleId[T <: AbstractTypeId, R <: TypeId](t: T): R = {
    (t match {
      case t: DTOId =>
        t.copy(pkg = fixPkg(t.pkg))

      case t: InterfaceId =>
        t.copy(pkg = fixPkg(t.pkg))

      case t: AdtId =>
        t.copy(pkg = fixPkg(t.pkg))

      case t: AliasId =>
        t.copy(pkg = fixPkg(t.pkg))

      case t: EnumId =>
        t.copy(pkg = fixPkg(t.pkg))

      case t: IdentifierId =>
        t.copy(pkg = fixPkg(t.pkg))

      case t: Builtin =>
        t
    }).asInstanceOf[R]
  }

  protected def fixIds[T <: AbstractTypeId, R <: TypeId](d: List[T]): List[R] = {
    d.map(fixId[T, R])
  }

  protected def fixFields(fields: raw.RawTuple): typed.Tuple = {
    fields.map(f => typed.Field(name = f.name, typeId = fixId[AbstractTypeId, TypeId](f.typeId)))
  }

  protected def fixPrimitiveFields(fields: raw.RawTuple): typed.PrimitiveTuple = {
    fields.map(f => typed.PrimitiveField(name = f.name, typeId = toPrimitive(f.typeId)))
  }


  protected def fixMethod(method: raw.Service.DefMethod): typed.Service.DefMethod = {
    method match {
      case m: raw.Service.DefMethod.DeprecatedMethod =>
        typed.Service.DefMethod.DeprecatedRPCMethod(signature = fixSignature(m.signature), name = m.name)
      case m: raw.Service.DefMethod.RPCMethod =>
        typed.Service.DefMethod.RPCMethod(signature = fixSignature(m.signature), name = m.name)
    }
  }

  protected def fixSignature(signature: raw.Service.DefMethod.Signature): typed.Service.DefMethod.Signature = {
    typed.Service.DefMethod.Signature(input = fixStructure(signature.input), output = fixOut(signature.output))
  }

  protected def fixOut(output: raw.Service.DefMethod.Output): typed.Service.DefMethod.Output = {
    output match {
      case o: raw.Service.DefMethod.Output.Usual =>
        typed.Service.DefMethod.Output.Usual(fixStructure(o.input))
      case o: raw.Service.DefMethod.Output.Algebraic =>
        typed.Service.DefMethod.Output.Algebraic(fixIds(o.alternatives))

    }
  }


  protected def fixStructure(s: raw.RawSimpleStructure): typed.SimpleStructure = {
    typed.SimpleStructure(concepts = fixIds(s.concepts), fields = fixFields(s.fields))
  }

  protected def fixSignature(signature: raw.Service.DefMethod.DeprecatedSignature): typed.Service.DefMethod.DeprecatedSignature = {
    typed.Service.DefMethod.DeprecatedSignature(input = fixIds(signature.input), output = fixIds(signature.output))
  }

  protected def fixPkg(pkg: common.Package): common.Package = {
    if (pkg.isEmpty) {
      domainId.toPackage
    } else {
      pkg
    }
  }

  protected def downcast(tid: AbstractTypeId): AbstractTypeId = {
    if (isPrimitive(tid)) {
      Primitive.mapping(tid.name)
    } else {
      tid
    }
  }

  protected def toPrimitive(typeId: AbstractTypeId): Primitive = {
    downcast(typeId) match {
      case p: Primitive =>
        p
      case o =>
        throw new IDLException(s"Unexpected non-primitive id: $o")
    }
  }

  protected def toScalar(typeId: TypeId): ScalarId = {
    typeId match {
      case p: Primitive =>
        p
      case o =>
        IdentifierId(o.pkg, o.name)
    }
  }

  protected def toGeneric(generic: IndefiniteGeneric): Generic = {
    generic.name match {
      case n if Generic.TSet.aliases.contains(n) =>
        Generic.TSet(makeDefinite(generic.args.head))

      case n if Generic.TList.aliases.contains(n) =>
        Generic.TList(makeDefinite(generic.args.head))

      case n if Generic.TOption.aliases.contains(n) =>
        Generic.TOption(makeDefinite(generic.args.head))

      case n if Generic.TMap.aliases.contains(n) =>
        Generic.TMap(toScalar(makeDefinite(generic.args.head)), makeDefinite(generic.args.last))
    }
  }

  protected def toIndefinite(typeId: AbstractTypeId): IndefiniteId = {
    IndefiniteId(fixPkg(typeId.pkg), typeId.name)
  }

  protected def isGeneric(abstractTypeId: AbstractTypeId): Boolean = {
    abstractTypeId.pkg.isEmpty && Generic.all.contains(abstractTypeId.name)
  }

  protected def isPrimitive(abstractTypeId: AbstractTypeId): Boolean = {
    abstractTypeId.pkg.isEmpty && Primitive.mapping.contains(abstractTypeId.name)
  }
}
