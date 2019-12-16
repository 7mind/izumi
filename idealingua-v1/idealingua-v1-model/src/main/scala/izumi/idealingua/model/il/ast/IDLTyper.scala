package izumi.idealingua.model.il.ast

import izumi.fundamentals.platform.exceptions.IzThrowable
import izumi.fundamentals.platform.strings.IzString._
import izumi.idealingua.model.common
import izumi.idealingua.model.common.TypeId._
import izumi.idealingua.model.common.{AbstractIndefiniteId, _}
import izumi.idealingua.model.il.ast.raw.defns.RawTypeDef.{ForeignType, NewType}
import izumi.idealingua.model.il.ast.raw.defns.RawVal.RawValScalar
import izumi.idealingua.model.il.ast.raw.defns._
import izumi.idealingua.model.il.ast.raw.domains.{DomainMeshLoaded, DomainMeshResolved, SingleImport}
import izumi.idealingua.model.il.ast.typed._
import izumi.idealingua.model.problems.{IDLDiagnostics, IDLException, TyperError}

import scala.collection.mutable
import scala.reflect._


class IDLTyper(defn: DomainMeshResolved) {
  def perform(): Either[IDLDiagnostics, typed.DomainDefinition] = {
    try {
      Right(new IDLPostTyper(new IDLPretyper(defn).perform()).perform())
    } catch {
      case t: Throwable =>
        import IzThrowable._
        Left(IDLDiagnostics(Vector(TyperError.TyperException(t.stackTrace))))
    }
  }
}


class IDLPretyper(defn: DomainMeshResolved) {
  def perform(): DomainMeshLoaded = {
    val types = defn.members.collect {
      case d: RawTopLevelDefn.TLDBaseType => d.v
      case d: RawTopLevelDefn.TLDNewtype => d.v
    }
    val imports = defn.imports.flatMap {
      i =>
        i.identifiers.map(SingleImport(i.id, _))
    }
    val services = defn.members.collect { case d: RawTopLevelDefn.TLDService => d.v }
    val buzzers = defn.members.collect { case d: RawTopLevelDefn.TLDBuzzer => d.v }
    val streams = defn.members.collect { case d: RawTopLevelDefn.TLDStreams => d.v }
    val consts = defn.members.collect { case d: RawTopLevelDefn.TLDConsts => d.v }

    val allImportNames = imports.map(_.imported.importedAs)
    val allTypeNames = defn.members.collect {
      case d: RawTopLevelDefn.TLDBaseType => d.v.id.name
      case d: RawTopLevelDefn.TLDNewtype => d.v.id.name
    }

    //    if (consts.nonEmpty) {
    //      TrivialLogger.make[IDLPretyper]("pretyper", forceLog = true).log(s"Constants aren't supported yet. Tree: ${consts.niceList()}")
    //    }

    val clashes = allImportNames.intersect(allTypeNames)
    if (clashes.nonEmpty) {
      throw new IDLException(s"[${defn.id}] Import names clashing with domain names: ${clashes.niceList()}")
    }
    DomainMeshLoaded(
      defn.id,
      defn.origin,
      defn.directInclusions,
      defn.imports,
      defn.meta,
      types,
      services,
      buzzers,
      streams,
      consts,
      imports,
      defn
    )
  }
}


class IDLPostTyper(defn: DomainMeshLoaded) {
  final val domainId: DomainId = defn.id

  private val domainCache = mutable.HashMap[DomainId, IDLPostTyper]()

  def getDomain(id: DomainId): IDLPostTyper = {
    val m = defn.defn.referenced(id)
    val typer = new IDLPostTyper(new IDLPretyper(m).perform())
    domainCache.put(id, typer)
    typer
  }

  protected val imported: Map[IndefiniteId, TypeId] = defn.imports
    .map {
      i =>
        val importedId = common.IndefiniteId(domainId.toPackage, i.imported.importedAs)
        val originalId = common.IndefiniteId(i.domain.toPackage, i.imported.name)
        toIndefinite(importedId) -> getDomain(i.domain).makeDefinite(originalId)
    }
    .toMap

  protected val mapping: Map[IndefiniteId, TypeId] = {
    defn.types
      .collect {
        case d: RawTypeDef.WithId =>
          toIndefinite(d.id) -> transformSimpleId[TypeId, TypeId](d.id)
      }
      .toMap ++ imported
  }

  protected val index: Map[IndefiniteId, RawTypeDef] = {
    defn.types.map {
      case d: RawTypeDef.WithId =>
        (toIndefinite(d.id), d)
      case d: NewType =>
        (toIndefinite(d.id.toIndefinite), d)
      case d: ForeignType =>
        (toIndefinite(d.id), d)
      case o =>
        throw new IllegalArgumentException(s"Unsupported typedef: $o")
    }.toMap
  }

  def perform(): typed.DomainDefinition = {
    val mappedTypes = defn.types.map(fixType)
    val mappedServices = defn.services.map(fixService)
    val mappedBuzzers = defn.buzzers.map(fixBuzzer)
    val mappedStreams = defn.streams.map(fixStreams)

    typed.DomainDefinition(
      id = domainId,
      meta = DomainMetadata(defn.origin, defn.directInclusions.map(i => typed.Inclusion(i.i)), defn.originalImports, fixMeta(defn.meta)),
      types = mappedTypes,
      services = mappedServices,
      buzzers = mappedBuzzers,
      streams = mappedStreams,
      referenced = domainCache.toMap //.mapValues(_.perform()).toMap
    )
  }

  protected def fixEnumMember(defn: RawEnumMember): typed.EnumMember = {
    EnumMember(defn.value, fixMeta(defn.meta))
  }

  protected def fixType(defn: RawTypeDef): typed.TypeDef = {
    defn match {
      case d: RawTypeDef.Enumeration =>
        typed.TypeDef.Enumeration(id = fixSimpleId(d.id): TypeId.EnumId, members = d.struct.members.map(fixEnumMember), meta = fixMeta(d.meta))

      case d: RawTypeDef.Alias =>
        typed.TypeDef.Alias(id = fixSimpleId(d.id): TypeId.AliasId, target = fixId(d.target): TypeId, meta = fixMeta(d.meta))

      case d: RawTypeDef.Identifier =>
        val typedFields = d.fields.map {
          case f if isIdPrimitive(f.typeId) =>
            IdField.PrimitiveField(toIdPrimitive(f.typeId), idNameFix(f, d.fields.size), fixMeta(f.meta))
          case f if mapping.get(toIndefinite(f.typeId)).exists(_.isInstanceOf[IdentifierId]) =>
            IdField.SubId(fixSimpleId(makeDefinite(f.typeId).asInstanceOf[IdentifierId]), idNameFix(f, d.fields.size), fixMeta(f.meta))
          case f if mapping.get(toIndefinite(f.typeId)).exists(_.isInstanceOf[EnumId]) =>
            IdField.Enum(fixSimpleId(makeDefinite(f.typeId).asInstanceOf[TypeId.EnumId]), idNameFix(f, d.fields.size), fixMeta(f.meta))
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

      case RawTypeDef.ForeignType(id, _, _) =>
        throw new IDLException(s"[$domainId] TODO: foreign type isn't supported yet for $id")

      case o =>
        throw new IllegalArgumentException(s"Unsupported typedef: $o")
    }
  }

  private def idNameFix(f: RawField, fieldsCount: Int) = {
    f.name match {
      case Some(value) =>
        value
      case None if fieldsCount == 1 =>
        "value"
      case None =>
        import izumi.fundamentals.platform.strings.IzString._
        val name = fixId[AbstractIndefiniteId, TypeId](f.typeId).name.uncapitalize
        if (name.startsWith("#")) {
          name.substring(1)
        } else {
          name
        }
    }
  }

  protected def translateValue(v: RawVal): ConstValue = {
    v match {
      case s: RawValScalar =>
        s match {
          case RawVal.CInt(value) =>
            ConstValue.CInt(value)
          case RawVal.CLong(value) =>
            ConstValue.CLong(value)
          case RawVal.CFloat(value) =>
            ConstValue.CFloat(value)
          case RawVal.CString(value) =>
            ConstValue.CString(value)
          case RawVal.CBool(value) =>
            ConstValue.CBool(value)
        }
      case RawVal.CMap(value) =>
        ConstValue.CMap(value.mapValues(translateValue).toMap)

      case RawVal.CList(value) =>
        ConstValue.CList(value.map(translateValue))

      case RawVal.CTypedList(typeId, value) =>
        val tpe = makeDefinite(typeId)
        val list = ConstValue.CList(value.map(translateValue))
        // TODO: verify structure
        ConstValue.CTypedList(tpe, list)
      case RawVal.CTyped(typeId, value) =>
        val tpe = makeDefinite(typeId)
        val typedValue = translateValue(value)
        // TODO: verify structure
        ConstValue.CTyped(tpe, typedValue)
      case RawVal.CTypedObject(typeId, value) =>
        val tpe = makeDefinite(typeId)
        val obj = ConstValue.CMap(value.mapValues(translateValue).toMap)
        // TODO: verify structure
        ConstValue.CTypedObject(tpe, obj)
    }
  }

  protected def fixAnno(v: RawAnno): Anno = {
    Anno(v.name, v.values.value.mapValues(translateValue).toMap, v.position)
  }

  protected def fixMeta(meta: RawNodeMeta): NodeMeta = {
    NodeMeta(meta.doc, meta.annos.map(fixAnno), meta.position)
  }

  protected def toMember(member: RawAdt.Member): typed.AdtMember = {
    member match {
      case RawAdt.Member.TypeRef(typeId, memberName, meta) =>
        typed.AdtMember(fixId(typeId): TypeId, memberName, fixMeta(meta))
      case RawAdt.Member.NestedDefn(nested) =>
        throw new IDLException(s"TODO: nested members are not supported yet: ${nested.id}")
    }

  }

  protected def toStruct(struct: RawStructure): typed.Structure = {
    typed.Structure(fields = fixFields(struct.fields), removedFields = fixFields(struct.removedFields), superclasses = toSuper(struct))
  }

  protected def toSuper(struct: RawStructure): typed.Super = {
    typed.Super(
      interfaces = fixSimpleIds(struct.interfaces)
      , concepts = fixMixinIds(struct.concepts)
      , removedConcepts = fixMixinIds(struct.removedConcepts)
    )
  }

  protected def fixService(defn: RawService): typed.Service = {
    typed.Service(id = fixServiceId(defn.id), methods = defn.methods.map(fixMethod), meta = fixMeta(defn.meta))
  }

  protected def fixBuzzer(defn: RawBuzzer): typed.Buzzer = {
    typed.Buzzer(id = fixBuzzerId(defn.id), events = defn.events.map(fixMethod), meta = fixMeta(defn.meta))
  }

  protected def fixStreams(defn: RawStreams): typed.Streams = {
    typed.Streams(id = fixStreamsId(defn.id), streams = defn.streams.map(fixStream), meta = fixMeta(defn.meta))
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

  protected def fixFields(fields: RawTuple): typed.Tuple = {
    fields.map{f =>
      typed.Field(name = idNameFix(f, fields.size), typeId = fixId[AbstractIndefiniteId, TypeId](f.typeId), meta = fixMeta(f.meta))}
  }

  protected def fixMethod(method: RawMethod): typed.DefMethod = {
    method match {
      case m: RawMethod.RPCMethod =>
        typed.DefMethod.RPCMethod(signature = fixSignature(m.signature), name = m.name, meta = fixMeta(m.meta))
    }
  }


  protected def fixStream(method: RawStream): typed.TypedStream = {
    method match {
      case m: RawStream.Directed =>
        typed.TypedStream.Directed(direction = m.direction, signature = fixStructure(m.signature), name = m.name, meta = fixMeta(m.meta))
    }
  }

  protected def fixSignature(signature: RawMethod.Signature): typed.DefMethod.Signature = {
    typed.DefMethod.Signature(input = fixStructure(signature.input), output = fixOut(signature.output))
  }

  protected def fixOut(output: RawMethod.Output): typed.DefMethod.Output = {
    output match {
      case o: RawMethod.Output.Alternative =>
        typed.DefMethod.Output.Alternative(fixNonAltOut(o.success), fixNonAltOut(o.failure))
      case o: RawMethod.Output.NonAlternativeOutput =>
        fixNonAltOut(o)
    }
  }

  protected def fixNonAltOut(output: RawMethod.Output.NonAlternativeOutput): typed.DefMethod.Output.NonAlternativeOutput = {
    output match {
      case o: RawMethod.Output.Struct =>
        typed.DefMethod.Output.Struct(fixStructure(o.input))
      case o: RawMethod.Output.Algebraic =>
        typed.DefMethod.Output.Algebraic(o.alternatives.map(toMember))
      case o: RawMethod.Output.Singular =>
        typed.DefMethod.Output.Singular(fixId(o.typeId): TypeId)
      case _: RawMethod.Output.Void =>
        typed.DefMethod.Output.Void()
    }
  }


  protected def fixStructure(s: RawSimpleStructure): typed.SimpleStructure = {
    typed.SimpleStructure(concepts = fixMixinIds(s.concepts), fields = fixFields(s.fields))
  }


  protected def makeDefinite(id: AbstractIndefiniteId): TypeId = {
    id match {
      case p if isPrimitive(p) =>
        Primitive.mapping(p.name)

      case g: IndefiniteGeneric =>
        toGeneric(g)

      case v if contains(v) =>
        lookupLocal(v)

      case v if !contains(v) =>
        lookupAnother(v)
    }
  }

  private def lookupLocal(v: AbstractIndefiniteId) = {
    val idefinite = toIndefinite(v)
    mapping.get(idefinite) match {
      case Some(t) =>
        t

      case None =>
        throw new IDLException(s"[$domainId] Type $idefinite is missing from this domain")
    }
  }

  private def lookupAnother(v: AbstractIndefiniteId) = {
    val referencedDomain = domainId(v.pkg)
    getDomain(referencedDomain).makeDefinite(v)
  }

  protected def domainId(v: Package): DomainId = {
    DomainId(v.init, v.last)
  }

  protected def toIdPrimitive(typeId: AbstractIndefiniteId): PrimitiveId = {
    typeId match {
      case p if isIdPrimitive(p) =>
        Primitive.mappingId(p.name)

      case o =>
        import izumi.fundamentals.platform.strings.IzString._
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
      //domainId.toPackage.zip(typeId.pkg).forall(ab => ab._1 == ab._2)
      domainId.toPackage == typeId.pkg
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
      mapping.get(toIndefinite(out)) match { // here we drop expected type and re-type through index. Solving https://github.com/7mind/izumi/issues/238
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
      getDomain(domainId(out.path.toPackage)).fixSimpleId(out)
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
