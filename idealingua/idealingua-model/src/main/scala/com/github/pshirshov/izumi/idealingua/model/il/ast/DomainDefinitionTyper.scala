package com.github.pshirshov.izumi.idealingua.model.il.ast

import com.github.pshirshov.izumi.idealingua.model.common
import com.github.pshirshov.izumi.idealingua.model.common.TypeId._
import com.github.pshirshov.izumi.idealingua.model.common._
import com.github.pshirshov.izumi.idealingua.model.exceptions.IDLException
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.{DomainDefinitionParsed, RawTypeDef}
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.IdField

import scala.reflect._


class DomainDefinitionTyper(defn: DomainDefinitionParsed) {
  final val domainId: DomainId = defn.id

  protected val mapping: Map[IndefiniteId, TypeId] = {
    defn.types.map(_.id)
      .map {
        kv =>
          toIndefinite(kv) -> transformSimpleId[TypeId, TypeId](kv)
      }
      .toMap
  }

  protected val index: Map[IndefiniteId, RawTypeDef] = defn.types.map(t => (toIndefinite(t.id), t)).toMap

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
        val typedFields = d.fields.map {
          case f if isIdPrimitive(f.typeId) =>
            IdField.PrimitiveField(toIdPrimitive(f.typeId), f.name)
          case f if mapping.get(toIndefinite(f.typeId)).exists(_.isInstanceOf[IdentifierId]) =>
            IdField.SubId(fixSimpleId(makeDefinite(f.typeId)): TypeId.IdentifierId, f.name)
          case f if mapping.get(toIndefinite(f.typeId)).exists(_.isInstanceOf[EnumId]) =>
            IdField.Enum(fixSimpleId(makeDefinite(f.typeId)): TypeId.EnumId, f.name)
          case f =>
            throw new IDLException(s"Unsupporeted ID field $f in $domainId. You may use primitive fields, enums or other IDs only")

        }

        typed.TypeDef.Identifier(id = fixSimpleId(d.id): TypeId.IdentifierId, fields = typedFields)

      case d: RawTypeDef.Interface =>
        typed.TypeDef.Interface(id = fixSimpleId(d.id): TypeId.InterfaceId, struct = toStruct(d.struct))

      case d: RawTypeDef.DTO =>
        typed.TypeDef.DTO(id = fixSimpleId(d.id), struct = toStruct(d.struct))

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
    typed.Super(
      interfaces = fixSimpleIds(struct.interfaces)
      , concepts = fixMixinIds(struct.concepts)
      , removedConcepts = fixMixinIds(struct.removedConcepts)
    )
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

  protected def fixSimpleIds[T <: TypeId : ClassTag](d: List[T]): List[T] = {
    d.map(fixSimpleId[T])
  }

  protected def fixMixinIds(d: List[IndefiniteMixin]): List[StructureId] = {
    d.map(makeDefiniteMixin)
  }

  protected def makeDefiniteMixin(m: IndefiniteMixin): StructureId = {
    mapping.get(toIndefinite(m)) match {
      case Some(v: DTOId) =>
        v
      case Some(v: InterfaceId) =>
        v
      case o =>
        throw new IDLException(s"[$domainId] Expected mixin at key $m, found $o")
    }
  }

  protected def fixFields(fields: raw.RawTuple): typed.Tuple = {
    fields.map(f => typed.Field(name = f.name, typeId = fixId[AbstractIndefiniteId, TypeId](f.typeId)))
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
    typed.SimpleStructure(concepts = fixMixinIds(s.concepts), fields = fixFields(s.fields))
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
            throw new IDLException(s"[$domainId] Type $id is missing from domain")
        }

      case v if !contains(v) =>
        val referencedDomain = domainId(v.pkg)
        defn.referenced.get(referencedDomain) match {
          case Some(d) =>
            new DomainDefinitionTyper(d).makeDefinite(v)
          case None =>
            throw new IDLException(s"[$domainId] Domain $referencedDomain is missing from context: ${defn.referenced.keySet.mkString("\n  ")}")
        }

    }
  }

  protected def domainId(v: Package) = {
    DomainId(v.init, v.last)
  }

  protected def toIdPrimitive(typeId: AbstractIndefiniteId): Primitive = {
    typeId match {
      case p if isIdPrimitive(p) =>
        Primitive.mappingId(p.name)

      case o =>
        import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._
        throw new IDLException(s"The $domainId: $o; Allowed types for identifier fields: ${Primitive.mappingId.values.map(_.name).niceList()}")
    }
  }

  protected def toScalar(typeId: TypeId): ScalarId = {
    typeId match {
      case p: Primitive =>
        p
      case o: IdentifierId =>
        o
      case o: EnumId =>
        o
      case o =>
        throw new IDLException(s"[$domainId] Unexpected non-scalar id at scalar place: $o")
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
        val indefinite = generic.args.head
        val definite = makeDefinite(indefinite)
        Generic.TMap(toScalar(definite), makeDefinite(generic.args.last))

      case o =>
        throw new IDLException(s"[$domainId] Unexpected generic: $o")
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

  protected def transformSimpleId[T <: TypeId, R <: TypeId](t: T): R = {
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

  protected def fixSimpleId[T <: TypeId : ClassTag](t: T): T = {
    val idType = classTag[T]

    val out = (t match {
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
    }).asInstanceOf[T]

    if (out.path.toPackage == domainId.toPackage) {
      mapping.get(toIndefinite(out)) match { // here we drop expected type and re-type through index. Solving https://github.com/pshirshov/izumi-r2/issues/238
        case Some(v: T) =>
          v

        case Some(v: AliasId) =>
          val replacement = index(toIndefinite(v)) match {
            case a: RawTypeDef.Alias =>
              makeDefinite(a.target) match {
                case t: T =>
                  fixSimpleId(t)
                case o =>
                  throw new IDLException(s"[$domainId]: failed to resolve id $t == $out: index contraction: $v expected to be $idType but it is $o")
              }
            case o =>
              throw new IDLException(s"[$domainId]: failed to resolve id $t == $out: index contraction: $v expected to be an alias but it is $o")
          }
          replacement

        case o =>
          throw new IDLException(s"[$domainId]: failed to resolve id $t == $out: expected to find $idType or an alias but got $o")
      }
    } else {
      new DomainDefinitionTyper(defn.referenced(domainId(out.path.toPackage))).fixSimpleId(out)
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

  protected def isIdPrimitive(abstractTypeId: AbstractIndefiniteId): Boolean = {
    abstractTypeId.pkg.isEmpty && Primitive.mappingId.contains(abstractTypeId.name)
  }

  protected def isPrimitive(abstractTypeId: AbstractIndefiniteId): Boolean = {
    abstractTypeId.pkg.isEmpty && Primitive.mapping.contains(abstractTypeId.name)
  }

}
