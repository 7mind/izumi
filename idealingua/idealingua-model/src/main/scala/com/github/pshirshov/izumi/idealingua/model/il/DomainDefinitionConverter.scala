package com.github.pshirshov.izumi.idealingua.model.il

import com.github.pshirshov.izumi.idealingua.model.common
import com.github.pshirshov.izumi.idealingua.model.common.TypeId._
import com.github.pshirshov.izumi.idealingua.model.common._
import com.github.pshirshov.izumi.idealingua.model.exceptions.IDLException
import com.github.pshirshov.izumi.idealingua.model.il.ILAst.Super


class DomainDefinitionConverter(defn: DomainDefinitionParsed) {
  final val domainId: DomainId = defn.id

  protected val mapping: Map[IndefiniteId, TypeId] = {
    defn.types.map(_.id)
      .map {
        kv =>
          toIndefinite(kv) -> fixSimpleId[AbstractTypeId, TypeId](kv)
      }
      .toMap
  }

  def convert(): DomainDefinition = {
    val mappedTypes = defn.types.map(fixType)
    val mappedServices = defn.services.map(fixService)
    val ref = defn.referenced.map(d => d._1 -> new DomainDefinitionConverter(d._2).convert())
    DomainDefinition(id = domainId, types = mappedTypes, services = mappedServices, referenced = ref)
  }

  protected def fixType(defn: ILAstParsed): ILAst = {
    defn match {
      case d: ILAstParsed.Enumeration =>
        ILAst.Enumeration(id = fixId(d.id), members = d.members)

      case d: ILAstParsed.Alias =>
        ILAst.Alias(id = fixId(d.id), target = fixId(d.target))

      case d: ILAstParsed.Identifier =>
        ILAst.Identifier(id = fixId(d.id), fields = fixPrimitiveFields(d.fields))

      case d: ILAstParsed.Interface =>
        val superclasses = Super(interfaces = fixIds(d.interfaces), concepts = fixIds(d.concepts))
        ILAst.Interface(id = fixId(d.id), fields = fixFields(d.fields), superclasses = superclasses)

      case d: ILAstParsed.DTO =>
        val superclasses = Super(interfaces = fixIds(d.interfaces), concepts = fixIds(d.concepts))
        ILAst.DTO(id = fixId(d.id), superclasses)

      case d: ILAstParsed.Adt =>
        ILAst.Adt(id = fixId(d.id), alternatives = fixIds(d.alternatives))
    }
  }

  protected def fixService(defn: ILAstParsed.Service): ILAst.Service = {
    ILAst.Service(id = fixServiceId(defn.id), methods = defn.methods.map(fixMethod))
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
            new DomainDefinitionConverter(d).makeDefinite(v)
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

  protected def fixFields(fields: ILAstParsed.Aggregate): ILAst.Tuple = {
    fields.map(f => ILAst.Field(name = f.name, typeId = fixId[AbstractTypeId, TypeId](f.typeId)))
  }

  protected def fixPrimitiveFields(fields: ILAstParsed.Aggregate): ILAst.PrimitiveTuple = {
    fields.map(f => ILAst.PrimitiveField(name = f.name, typeId = toPrimitive(f.typeId)))
  }


  protected def fixMethod(method: ILAstParsed.Service.DefMethod): ILAst.Service.DefMethod = {
    method match {
      case m: ILAstParsed.Service.DefMethod.RPCMethod =>
        ILAst.Service.DefMethod.RPCMethod(signature = fixSignature(m.signature), name = m.name)
    }
  }

  protected def fixSignature(signature: ILAstParsed.Service.DefMethod.Signature): ILAst.Service.DefMethod.Signature = {
    ILAst.Service.DefMethod.Signature(input = fixIds(signature.input), output = fixIds(signature.output))
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
      case "set" =>
        Generic.TSet(makeDefinite(generic.args.head))

      case "list" =>
        Generic.TList(makeDefinite(generic.args.head))

      case "opt" =>
        Generic.TOption(makeDefinite(generic.args.head))

      case "map" =>
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
