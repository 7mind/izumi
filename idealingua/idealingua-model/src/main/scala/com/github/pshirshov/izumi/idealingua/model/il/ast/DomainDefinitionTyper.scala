package com.github.pshirshov.izumi.idealingua.model.il.ast

import com.github.pshirshov.izumi.idealingua.model.common
import com.github.pshirshov.izumi.idealingua.model.common.TypeId._
import com.github.pshirshov.izumi.idealingua.model.common._
import com.github.pshirshov.izumi.idealingua.model.exceptions.IDLException
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.{DomainDefinitionParsed, RawTypeDef}


class DomainDefinitionTyper(defn: DomainDefinitionParsed) {
  final val domainId: DomainId = defn.id

  protected val mapping: Map[IndefiniteId, TypeId] = {
    defn.types.map(_.id)
      .map {
        kv =>
          toIndefinite(kv) -> fixSimpleId[TypeId, TypeId](kv)
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
        typed.TypeDef.Enumeration(id = fixSimpleId(d.id): TypeId.EnumId, members = d.members)

      case d: RawTypeDef.Alias =>
        typed.TypeDef.Alias(id = fixSimpleId(d.id): TypeId.AliasId, target = fixId(d.target): TypeId)

      case d: RawTypeDef.Identifier =>
        typed.TypeDef.Identifier(id = fixSimpleId(d.id): TypeId.IdentifierId, fields = fixPrimitiveFields(d.fields))

      case d: RawTypeDef.Interface =>
        typed.TypeDef.Interface(id = fixSimpleId(d.id): TypeId.InterfaceId, struct = toStruct(d.struct))

      case d: RawTypeDef.DTO =>
        typed.TypeDef.DTO(id = fixSimpleId(d.id): TypeId.DTOId, struct = toStruct(d.struct))

      case d: RawTypeDef.Adt =>
        typed.TypeDef.Adt(id = fixSimpleId(d.id): TypeId.AdtId, alternatives = d.alternatives.map(toMember))
    }
  }

  protected def toMember(member: raw.RawAdtMember): typed.AdtMember = {
    typed.AdtMember(fixId(member.typeId): TypeId, member.memberName)
  }

  protected def toStruct(struct: raw.RawStructure): typed.Structure = {
    typed.Structure(fields = fixFields(struct.fields), removedFields = fixFields(struct.removedFields), superclasses = toSuper(struct))
  }

  protected def toSuper(struct: raw.RawStructure): typed.Super = {
    typed.Super(interfaces = fixSimpleIds(struct.interfaces), concepts = fixSimpleIds(struct.concepts), removedConcepts = fixSimpleIds(struct.removedConcepts))
  }

  protected def fixService(defn: raw.Service): typed.Service = {
    typed.Service(id = fixServiceId(defn.id), methods = defn.methods.map(fixMethod))
  }


  protected def fixId[T <: AbstractIndefiniteId, R <: TypeId](t: T): R = {
    (t match {
      case t: IndefiniteId =>
        makeDefinite(t)

      case t: IndefiniteGeneric =>
        makeDefinite(t)
    }).asInstanceOf[R]
  }

  protected def fixSimpleIds[T <: TypeId, R <: TypeId](d: List[T]): List[R] = {
    d.map(fixSimpleId[T, R])
  }

  protected def fixFields(fields: raw.RawTuple): typed.Tuple = {
    fields.map(f => typed.Field(name = f.name, typeId = fixId[AbstractIndefiniteId, TypeId](f.typeId)))
  }

  protected def fixPrimitiveFields(fields: raw.RawTuple): typed.PrimitiveTuple = {
    fields.map(f => typed.PrimitiveField(name = f.name, typeId = toPrimitive(f.typeId)))
  }


  protected def fixMethod(method: raw.Service.DefMethod): typed.Service.DefMethod = {
    method match {
      case m: raw.Service.DefMethod.RPCMethod =>
        typed.Service.DefMethod.RPCMethod(signature = fixSignature(m.signature), name = m.name)
    }
  }

  protected def fixSignature(signature: raw.Service.DefMethod.Signature): typed.Service.DefMethod.Signature = {
    typed.Service.DefMethod.Signature(input = fixStructure(signature.input), output = fixOut(signature.output))
  }

  protected def fixOut(output: raw.Service.DefMethod.Output): typed.Service.DefMethod.Output = {
    output match {
      case o: raw.Service.DefMethod.Output.Struct =>
        typed.Service.DefMethod.Output.Struct(fixStructure(o.input))
      case o: raw.Service.DefMethod.Output.Algebraic =>
        typed.Service.DefMethod.Output.Algebraic(o.alternatives.map(toMember))
      case o: raw.Service.DefMethod.Output.Singular =>
        typed.Service.DefMethod.Output.Singular(fixId(o.typeId): TypeId)
    }
  }


  protected def fixStructure(s: raw.RawSimpleStructure): typed.SimpleStructure = {
    typed.SimpleStructure(concepts = fixSimpleIds(s.concepts), fields = fixFields(s.fields))
  }


  protected def makeDefinite(id: AbstractIndefiniteId): TypeId = {
    id match {
      case p if isPrimitive(p) =>
        Primitive.mapping(p.name)

      case g: IndefiniteGeneric =>
        toGeneric(g)

      case v if contains(v) =>
        mapping.get(toIndefinite(v)) match {
          case Some(t) =>
            t
          case None =>
            throw new IDLException(s"Type $id is missing from domain $domainId")
        }

      case v if !contains(v) =>
        val referencedDomain = DomainId(v.pkg.init, v.pkg.last)
        defn.referenced.get(referencedDomain) match {
          case Some(d) =>
            new DomainDefinitionTyper(d).makeDefinite(v)
          case None =>
            throw new IDLException(s"Domain $referencedDomain is missing from context of $domainId")
        }

    }
  }

  protected def toPrimitive(typeId: AbstractIndefiniteId): Primitive = {
    typeId match {
      case p if isPrimitive(p) =>
        Primitive.mapping(p.name)

      case o =>
        throw new IDLException(s"Unexpected non-primitive id: $o")
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


  protected def contains(typeId: AbstractIndefiniteId): Boolean = {
    if (typeId.pkg.isEmpty) {
      true
    } else {
      domainId.toPackage.zip(typeId.pkg).forall(ab => ab._1 == ab._2)
    }
  }


  protected def fixServiceId(t: ServiceId): ServiceId = {
    t.copy(domain = domainId)
  }

  protected def fixSimpleId[T <: TypeId, R <: TypeId](t: T): R = {
    (t match {
      case t: DTOId =>
        t.copy(path = fixPkg(t.path))

      case t: InterfaceId =>
        t.copy(path = fixPkg(t.path))

      case t: AdtId =>
        t.copy(path = fixPkg(t.path))

      case t: AliasId =>
        t.copy(path = fixPkg(t.path))

      case t: EnumId =>
        t.copy(path = fixPkg(t.path))

      case t: IdentifierId =>
        t.copy(path = fixPkg(t.path))

      case t: Builtin =>
        t
    }).asInstanceOf[R]
  }

  protected def toScalar(typeId: TypeId): ScalarId = {
    typeId match {
      case p: Primitive =>
        p
      case o =>
        IdentifierId(o.path, o.name)
    }
  }

  protected def toIndefinite(typeId: TypeId): IndefiniteId = {
    typeId.path.domain match {
      case DomainId.Undefined =>
        IndefiniteId(domainId.toPackage, typeId.name)

      case _ =>
        IndefiniteId(typeId.path.toPackage, typeId.name)
    }
  }

  protected def toIndefinite(typeId: AbstractIndefiniteId): IndefiniteId = {
    if (typeId.pkg.isEmpty) {
      IndefiniteId(domainId.toPackage, typeId.name)
    } else {
      IndefiniteId(typeId.pkg, typeId.name)
    }
  }


  protected def fixPkg(pkg: TypePath): common.TypePath = {
    pkg.domain match {
      case DomainId.Undefined =>
        pkg.copy(domain = domainId)
      case _ =>
        pkg
    }
  }

  protected def isPrimitive(abstractTypeId: AbstractIndefiniteId): Boolean = {
    abstractTypeId.pkg.isEmpty && Primitive.mapping.contains(abstractTypeId.name)
  }


}
