package com.github.pshirshov.izumi.idealingua.model.il.ast

import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._
import com.github.pshirshov.izumi.idealingua.model.common
import com.github.pshirshov.izumi.idealingua.model.common.TypeId._
import com.github.pshirshov.izumi.idealingua.model.common.{AbstractIndefiniteId, _}
import com.github.pshirshov.izumi.idealingua.model.exceptions.IDLException
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.IL._
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.RawTypeDef.NewType
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.RawVal.RawValScalar
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw._
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.{Anno, IdField, NodeMeta}

import scala.reflect._

class IDLTyper(defn: DomainDefinitionParsed) {
  def perform(): typed.DomainDefinition = {
    new IDLPostTyper(new IDLPretyper(defn).perform()).perform()
  }
}


class IDLPretyper(defn: DomainDefinitionParsed) {
  def perform(): DomainDefinitionInterpreted = {
    val types = defn.members.collect {
      case d: ILDef => d.v
      case d: ILNewtype => d.v
    }
    val imports = defn.members.collect({ case d: ILImport => d })
    val services = defn.members.collect({ case d: ILService => d.v })
    val buzzers = defn.members.collect({ case d: ILBuzzer => d.v })
    val streams = defn.members.collect({ case d: ILStreams => d.v })
    val consts = defn.members.collect({ case d: ILConst => d.v })

    val allImportNames = imports.map(_.id.importedName)
    val allTypeNames = defn.members.collect {
      case d: ILDef => d.v.id.name
      case d: ILNewtype => d.v.id.name
    }

    //    if (consts.nonEmpty) {
    //      TrivialLogger.make[IDLPretyper]("pretyper", forceLog = true).log(s"Constants aren't supported yet. Tree: ${consts.niceList()}")
    //    }

    val clashes = allImportNames.intersect(allTypeNames)
    if (clashes.nonEmpty) {
      throw new IDLException(s"[${defn.id}] Import names clashing with domain names: ${clashes.niceList()}")
    }

    DomainDefinitionInterpreted(
      defn.id,
      types,
      services,
      buzzers,
      streams,
      consts,
      imports,
      defn.referenced.mapValues(r => new IDLPretyper(r).perform())
    )
  }
}

class IDLPostTyper(defn: DomainDefinitionInterpreted) {
  final val domainId: DomainId = defn.id

  protected def refs: Map[DomainId, IDLPostTyper] = defn.referenced.map(d => d._1 -> new IDLPostTyper(d._2))

  protected val imported: Map[IndefiniteId, TypeId] = defn.imports
    .map {
      i =>
        val importedId = common.IndefiniteId(domainId.toPackage, i.id.importedName)
        val originalId = common.IndefiniteId(i.domain.toPackage, i.id.name)
        toIndefinite(importedId) -> refs(i.domain).makeDefinite(originalId)
    }
    .toMap

  protected val mapping: Map[IndefiniteId, TypeId] = {
    defn.types
      .collect {
        case d: IdentifiedRawTypeDef =>
          toIndefinite(d.id) -> transformSimpleId[TypeId, TypeId](d.id)
      }
      .toMap ++ imported
  }

  protected val index: Map[IndefiniteId, RawTypeDef] = {
    defn.types.map {
      case d: IdentifiedRawTypeDef =>
        (toIndefinite(d.id), d)
      case d: NewType =>
        (toIndefinite(d.id.toTypeId), d)
    }.toMap
  }

  def perform(): typed.DomainDefinition = {
    val mappedTypes = defn.types.map(fixType)
    val mappedServices = defn.services.map(fixService)
    val mappedBuzzers = defn.buzzers.map(fixBuzzer)
    val mappedStreams = defn.streams.map(fixStreams)

    typed.DomainDefinition(
      id = domainId,
      types = mappedTypes,
      services = mappedServices,
      buzzers = mappedBuzzers,
      streams = mappedStreams,
      referenced = refs.mapValues(_.perform())
    )
  }

  protected def fixType(defn: RawTypeDef): typed.TypeDef = {
    defn match {
      case d: RawTypeDef.Enumeration =>
        typed.TypeDef.Enumeration(id = fixSimpleId(d.id): TypeId.EnumId, members = d.members, meta = fixMeta(d.meta))

      case d: RawTypeDef.Alias =>
        typed.TypeDef.Alias(id = fixSimpleId(d.id): TypeId.AliasId, target = fixId(d.target): TypeId, meta = fixMeta(d.meta))

      case d: RawTypeDef.Identifier =>
        val typedFields = d.fields.map {
          case f if isIdPrimitive(f.typeId) =>
            IdField.PrimitiveField(toIdPrimitive(f.typeId), f.name)
          case f if mapping.get(toIndefinite(f.typeId)).exists(_.isInstanceOf[IdentifierId]) =>
            IdField.SubId(fixSimpleId(makeDefinite(f.typeId).asInstanceOf[IdentifierId]), f.name)
          case f if mapping.get(toIndefinite(f.typeId)).exists(_.isInstanceOf[EnumId]) =>
            IdField.Enum(fixSimpleId(makeDefinite(f.typeId).asInstanceOf[TypeId.EnumId]), f.name)
          case f =>
            throw new IDLException(s"[$domainId] Unsupporeted ID field $f while handling ${d.id} You may use primitive fields, enums or other IDs only")
        }

        typed.TypeDef.Identifier(id = fixSimpleId(d.id): TypeId.IdentifierId, fields = typedFields, meta = fixMeta(d.meta))

      case d: RawTypeDef.Interface =>
        typed.TypeDef.Interface(id = fixSimpleId(d.id): TypeId.InterfaceId, struct = toStruct(d.struct), meta = fixMeta(d.meta))

      case d: RawTypeDef.DTO =>
        typed.TypeDef.DTO(id = fixSimpleId(d.id), struct = toStruct(d.struct), meta = fixMeta(d.meta))

      case d: RawTypeDef.Adt =>
        typed.TypeDef.Adt(id = fixSimpleId(d.id): TypeId.AdtId, alternatives = d.alternatives.map(toMember), meta = fixMeta(d.meta))

      case RawTypeDef.NewType(id, src, None, c) =>
        typed.TypeDef.Alias(id = transformSimpleId(id.toAliasId): TypeId.AliasId, target = fixId(src): TypeId, meta = fixMeta(c))

      case RawTypeDef.NewType(id, src, Some(m), c) =>
        val source = index(toIndefinite(src))
        source match {
          case s: RawTypeDef.DTO =>
            val extended = s.copy(struct = s.struct.extend(m))
            val fixed = fixType(extended.copy(struct = extended.struct.copy(concepts = IndefiniteMixin(src.pkg, src.name) +: extended.struct.concepts)))
              .asInstanceOf[typed.TypeDef.DTO]
            fixed.copy(id = fixed.id.copy(name = id.name), meta = fixMeta(c))
          case s: RawTypeDef.Interface =>
            val extended = s.copy(struct = s.struct.extend(m))
            val fixed = fixType(extended.copy(struct = extended.struct.copy(concepts = IndefiniteMixin(src.pkg, src.name) +: extended.struct.concepts)))
              .asInstanceOf[typed.TypeDef.Interface]
            fixed.copy(id = fixed.id.copy(name = id.name), meta = fixMeta(c))
          case o =>
            throw new IDLException(s"[$domainId] TODO: newtype isn't supported yet for $o")
        }
    }
  }

  protected def translateValue(v: RawVal[Any]): Any = {
    v match {
      case RawVal.CMap(value) =>
        value.mapValues(translateValue)
      case RawVal.CList(value) =>
        value.map(translateValue)
      case s: RawValScalar[_] =>
        s.value
      case o =>
        throw new IDLException(s"[$domainId] Value isn't supported in annotations $o")
    }
  }

  protected def fixAnno(v: RawAnno): Anno = {
    Anno(v.name, v.values.value.mapValues(translateValue))
  }

  protected def fixMeta(meta: RawNodeMeta): NodeMeta = {
    NodeMeta(meta.doc, meta.annos.map(fixAnno))
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
    typed.Service(id = fixServiceId(defn.id), methods = defn.methods.map(fixMethod), doc = fixMeta(defn.meta))
  }

  protected def fixBuzzer(defn: raw.Buzzer): typed.Buzzer = {
    typed.Buzzer(id = fixBuzzerId(defn.id), events = defn.events.map(fixMethod), doc = fixMeta(defn.meta))
  }

  protected def fixStreams(defn: raw.Streams): typed.Streams = {
    typed.Streams(id = fixStreamsId(defn.id), streams = defn.streams.map(fixStream), doc = fixMeta(defn.meta))
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

  protected def fixMethod(method: raw.RawMethod): typed.DefMethod = {
    method match {
      case m: raw.RawMethod.RPCMethod =>
        typed.DefMethod.RPCMethod(signature = fixSignature(m.signature), name = m.name, meta = fixMeta(m.meta))
    }
  }


  protected def fixStream(method: raw.RawStream): typed.TypedStream = {
    method match {
      case m: raw.RawStream.Directed =>
        typed.TypedStream.Directed(direction = m.direction, signature = fixStructure(m.signature), name = m.name, meta = fixMeta(m.meta))
    }
  }

  protected def fixSignature(signature: raw.RawMethod.Signature): typed.DefMethod.Signature = {
    typed.DefMethod.Signature(input = fixStructure(signature.input), output = fixOut(signature.output))
  }

  protected def fixOut(output: raw.RawMethod.Output): typed.DefMethod.Output = {
    output match {
      case o: raw.RawMethod.Output.Alternative =>
        typed.DefMethod.Output.Alternative(fixNonAltOut(o.success), fixNonAltOut(o.failure))
      case o: raw.RawMethod.Output.NonAlternativeOutput =>
        fixNonAltOut(o)
    }
  }

  protected def fixNonAltOut(output: raw.RawMethod.Output.NonAlternativeOutput): typed.DefMethod.Output.NonAlternativeOutput = {
    output match {
      case o: raw.RawMethod.Output.Struct =>
        typed.DefMethod.Output.Struct(fixStructure(o.input))
      case o: raw.RawMethod.Output.Algebraic =>
        typed.DefMethod.Output.Algebraic(o.alternatives.map(toMember))
      case o: raw.RawMethod.Output.Singular =>
        typed.DefMethod.Output.Singular(fixId(o.typeId): TypeId)
      case _: raw.RawMethod.Output.Void =>
        typed.DefMethod.Output.Void()
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
        val idefinite = toIndefinite(v)
        mapping.get(idefinite) match {
          case Some(t) =>
            t
          case None =>
            throw new IDLException(s"[$domainId] Type $idefinite is missing from domain")
        }

      case v if !contains(v) =>
        val referencedDomain = domainId(v.pkg)
        refs.get(referencedDomain) match {
          case Some(typer) =>
            typer.makeDefinite(v)
          case None =>
            throw new IDLException(s"[$domainId] Type $id references domain $referencedDomain but that domain wasn't imported. Imported domains: ${defn.referenced.keySet.mkString("\n  ")}")
        }

    }
  }

  protected def domainId(v: Package): DomainId = {
    DomainId(v.init, v.last)
  }

  protected def toIdPrimitive(typeId: AbstractIndefiniteId): PrimitiveId = {
    typeId match {
      case p if isIdPrimitive(p) =>
        Primitive.mappingId(p.name)

      case o =>
        import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._
        throw new IDLException(s"[$domainId] Non-primitive type $o but primitive expected. Allowed types for identifier fields: ${Primitive.mappingId.values.map(_.name).niceList()}")
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

  protected def fixBuzzerId(t: BuzzerId): BuzzerId = {
    t.copy(domain = domainId)
  }

  protected def fixStreamsId(t: StreamsId): StreamsId = {
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
      refs(domainId(out.path.toPackage)).fixSimpleId(out)
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
