package com.github.pshirshov.izumi.idealingua.typer2

import com.github.pshirshov.izumi.idealingua.model.common._
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns.RawAdt.Member
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns.{RawField, RawNodeMeta, RawStructure, RawTypeDef}
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.typeid.ParsedId
import com.github.pshirshov.izumi.idealingua.typer2.model.IzType.model._
import com.github.pshirshov.izumi.idealingua.typer2.model.IzType.{Adt, BuiltinType, DTO, Enum, Foreign, ForeignGeneric, ForeignScalar, Generic, Identifier, Interface, Interpolation, IzAlias, IzStructure}
import com.github.pshirshov.izumi.idealingua.typer2.model.IzTypeId.model._
import com.github.pshirshov.izumi.idealingua.typer2.model.IzTypeReference.model.{IzTypeArg, IzTypeArgName, IzTypeArgValue}
import com.github.pshirshov.izumi.idealingua.typer2.model.T2Fail._
import com.github.pshirshov.izumi.idealingua.typer2.model.{IzType, IzTypeId, IzTypeReference, T2Warn}

import scala.reflect.ClassTag


class Interpreter(_index: DomainIndex, types: Map[IzTypeId, ProcessedOp], logger: WarnLogger) {
  private val index: DomainIndex = _index

  def makeForeign(v: RawTypeDef.ForeignType): TSingle = {
    v.id match {
      case IndefiniteGeneric(pkg, name, args) =>
        val id = index.toId(Seq.empty, index.makeAbstract(IndefiniteId(pkg, name)))

        val badargs = args.filter(_.pkg.nonEmpty)
        if (badargs.isEmpty) {
          val params = args.map(a => IzTypeArgName(a.name))
          Right(ForeignGeneric(id, params, v.mapping.mapValues(ctx => Interpolation(ctx.parts, ctx.parameters.map(IzTypeArgName))), meta(v.meta)))
        } else {
          Left(List(BadArguments(id, badargs)))
        }

      case _ =>
        val id = index.toId(Seq.empty, index.makeAbstract(v.id))
        val badMappings = v.mapping.values.filter(ctx => ctx.parameters.nonEmpty || ctx.parts.size != 1)

        if (badMappings.isEmpty) {
          Right(ForeignScalar(id, v.mapping.mapValues(_.parts.head), meta(v.meta)))
        } else {
          Left(List(UnexpectedArguments(id, badMappings.toSeq)))
        }
    }
  }

  def makeAdt(a: RawTypeDef.Adt): TList = {
    makeAdt(a, Seq.empty).map(_.flatten)
  }

  case class Chain(main: IzType, additional: List[IzType]) {
    def flatten: List[IzType] = List(main) ++ additional
  }

  case class Pair(member: IzType.model.AdtMember, additional: List[IzType])

  type TChain = Either[List[BuilderFail], Chain]

  implicit class TSingleExt1[T <: IzType](ret: TSingleT[T]) {
    def asChain: TChain = ret.map(r => Chain(r, List.empty))
  }

  private def makeAdt(a: RawTypeDef.Adt, subpath: Seq[IzNamespace]): TChain = {
    val id = toId(a.id, subpath)
    val members = a.alternatives.map {
      case Member.TypeRef(typeId, memberName, m) =>
        val tpe = resolve(typeId)
        val name = tpe match {
          case IzTypeReference.Scalar(mid) =>
            memberName.getOrElse(mid.name.name)
          case IzTypeReference.Generic(_, _) =>
            memberName match {
              case Some(value) =>
                value
              case None =>
                ??? // name must be defined for generic members
            }
        }
        Pair(AdtMemberRef(name, tpe, meta(m)), List.empty)

      case Member.NestedDefn(nested) =>
        val tpe = nested match {
          case n: RawTypeDef.Interface =>
            makeInterface(n, subpath).asChain
          case n: RawTypeDef.DTO =>
            makeDto(n, subpath).asChain
          case n: RawTypeDef.Enumeration =>
            makeEnum(n, subpath).asChain
          case n: RawTypeDef.Alias =>
            makeAlias(n, subpath).asChain
          case n: RawTypeDef.Identifier =>
            makeIdentifier(n, subpath).asChain
          case n: RawTypeDef.Adt =>
            makeAdt(n, subpath :+ IzNamespace(n.id.name))
        }
        tpe match {
          case Left(_) =>
            ???
          case Right(value) =>
            Pair(AdtMemberNested(nested.id.name, IzTypeReference.Scalar(value.main.id), meta(nested.meta)), value.additional)
        }

    }

    val adtMembers = members.map(_.member)
    val associatedTypes = members.flatMap(_.additional)

    Right(Chain(Adt(id, adtMembers, meta(a.meta)), associatedTypes))
  }

  def cloneType(v: RawTypeDef.NewType): TList = {
    val id = resolveId(v.id)
    val sid = index.resolveId(v.source)
    val source = types(sid)

    // TODO: we need to support more modifiers and interface removal for structures
    source.member match {
      case d: DTO =>
        for {
          modified <- modify(id, d, v.modifiers)
          product <- make[DTO](modified, id, v.meta).map(t => List(t))
        } yield {
          product
        }

      case d: Interface =>
        for {
          modified <- modify(id, d, v.modifiers)
          product <- make[Interface](modified, id, v.meta).map(t => List(t))
        } yield {
          product
        }

      case builtinType: BuiltinType =>
        if (v.modifiers.isEmpty) {
          Right(List(IzAlias(id, IzTypeReference.Scalar(builtinType.id), meta(v.meta))))
        } else {
          Left(List(IncompatibleCloneModifiers(id, v.modifiers.nonEmpty)))
        }

      case a: IzAlias =>
        if (v.modifiers.isEmpty) {
          Right(List(a.copy(id = id)))
        } else {
          Left(List(IncompatibleCloneModifiers(id, v.modifiers.nonEmpty)))
        }

      case i: Identifier =>
        if (v.modifiers.isEmpty) {
          Right(List(i.copy(id = id)))
        } else {
          Left(List(IncompatibleCloneModifiers(id, v.modifiers.nonEmpty)))
        }

      case e: Enum =>
        if (v.modifiers.isEmpty) {
          Right(List(e.copy(id = id)))
        } else {
          Left(List(IncompatibleCloneModifiers(id, v.modifiers.nonEmpty)))
        }

      case g: Generic =>
        if (v.modifiers.isEmpty) {
          g match {
            case fg: ForeignGeneric =>
              Right(List(fg.copy(id = id, meta = meta(v.meta))))

            case generic: IzType.BuiltinGeneric =>
              Left(List(FeatureUnsupported(id, "TODO: Builtin generic cloning is almost meaningless and not supported yet (we need to support templates first)")))
          }
        } else {
          Left(List(IncompatibleCloneModifiers(id, v.modifiers.nonEmpty)))
        }

      case f: Foreign =>
        if (v.modifiers.isEmpty) {
          f match {
            case fs: ForeignScalar =>
              Right(List(fs.copy(id = id, meta = meta(v.meta))))

            case fg: ForeignGeneric =>
              Right(List(fg.copy(id = id, meta = meta(v.meta))))
          }
        } else {
          Left(List(IncompatibleCloneModifiers(id, v.modifiers.nonEmpty)))
        }

      case a: Adt =>
        if (v.modifiers.isEmpty) {
          // TODO: we need to support branch adding/removing for ADTs
          Right(List(a.copy(id = id, meta = meta(v.meta))))
        } else {
          Left(List(IncompatibleCloneModifiers(id, v.modifiers.nonEmpty)))
        }

    }

  }

  def makeIdentifier(i: RawTypeDef.Identifier): TSingle = {
    makeIdentifier(i, Seq.empty)
  }

  def makeEnum(e: RawTypeDef.Enumeration): TSingle = {
    makeEnum(e, Seq.empty)
  }

  def makeInterface(i: RawTypeDef.Interface): TSingle = {
    makeInterface(i, Seq.empty)
  }

  def makeDto(i: RawTypeDef.DTO): TSingle = {
    makeDto(i, Seq.empty)
  }

  def makeAlias(a: RawTypeDef.Alias): TSingle = {
    makeAlias(a, Seq.empty)
  }

  private def modify(context: IzTypeId, source: IzType, modifiers: Option[RawStructure]): Either[List[BuilderFail], RawStructure] = {
    source match {
      case structure: IzStructure =>
        val struct = structure.defn
        Right(modifiers.map(m => mergeStructs(struct, m)).getOrElse(struct))
      case _ =>
        Left(List(StructureExpected(context, source.id)))
    }
  }

  private def mergeStructs(struct: RawStructure, value: RawStructure): RawStructure = {
    struct.copy(
      interfaces = struct.interfaces ++ value.interfaces,
      concepts = struct.concepts ++ value.concepts,
      removedConcepts = struct.removedConcepts ++ value.removedConcepts,
      fields = struct.fields ++ value.removedFields,
      removedFields = struct.removedFields ++ value.removedFields,
    )
  }


  private def makeIdentifier(i: RawTypeDef.Identifier, subpath: Seq[IzNamespace]): TSingleT[IzType.Identifier] = {
    val id = toId(i.id, subpath)

    val fields = i.fields.zipWithIndex.map {
      case (f, idx) =>
        val ref = toRef(f)
        FullField(fname(f), ref, Seq(FieldSource(id, ref, idx, 0, meta(f.meta))))
    }
    Right(Identifier(id, fields, meta(i.meta)))
  }


  private def makeEnum(e: RawTypeDef.Enumeration, subpath: Seq[IzNamespace]): TSingle = {
    val id = toId(e.id, subpath)
    val parents = e.struct.parents.map(toTopId).map(types.apply).map(_.member)

    for {
      parentMembers <- parents.map(enumMembers(id)).biFlatAggregate
    } yield {
      val localMembers = e.struct.members.map {
        m =>
          EnumMember(m.value, None, meta(m.meta))
      }
      val removedFields = e.struct.removed.toSet
      val allMembers = (parentMembers ++ localMembers).filterNot(m => removedFields.contains(m.name))
      Enum(id, allMembers, meta(e.meta))
    }
  }


  private def makeInterface(i: RawTypeDef.Interface, subpath: Seq[IzNamespace]): TSingle = {
    val struct = i.struct
    val id = toId(i.id, subpath)
    make[IzType.Interface](struct, id, i.meta)
  }


  private def makeDto(i: RawTypeDef.DTO, subpath: Seq[IzNamespace]): TSingle = {
    val struct = i.struct
    val id = toId(i.id, subpath)
    make[IzType.DTO](struct, id, i.meta)
  }


  private def makeAlias(a: RawTypeDef.Alias, subpath: Seq[IzNamespace]): TSingleT[IzType.IzAlias] = {
    val id = toId(a.id, subpath)
    Right(IzAlias(id, resolve(a.target), meta(a.meta)))
  }

  private def resolveId(id: ParsedId): IzTypeId = {
    index.resolveId(id.toIndefinite)
  }


  private def resolve(id: AbstractIndefiniteId): IzTypeReference = {
    id match {
      case nongeneric: AbstractNongeneric =>
        IzTypeReference.Scalar(index.resolveId(nongeneric))

      case generic: IndefiniteGeneric =>
        // this is not good
        val id = index.resolveId(IndefiniteId(generic.pkg, generic.name))
        IzTypeReference.Generic(id, generic.args.zipWithIndex.map {
          case (a, idx) =>
            val argValue = resolve(a)
            IzTypeArg(IzTypeArgName(idx.toString), IzTypeArgValue(argValue))
        })
    }
  }


  private def toTopId(id: TypeId): IzTypeId = {
    toId(id, Seq.empty)
  }

  private def toId(id: TypeId, subpath: Seq[IzNamespace]): IzTypeId = {
    assert(id.path.within.isEmpty, s"BUG: Unexpected TypeId, path should be empty: $id")
    val namespace = subpath
    val unresolvedName = index.makeAbstract(id)
    index.toId(namespace, unresolvedName)
  }


  private def make[T <: IzStructure : ClassTag](struct: RawStructure, id: IzTypeId, structMeta: RawNodeMeta): TSingle = {
    val parentsIds = struct.interfaces.map(toTopId)
    val parents = parentsIds.map(types.apply).map(_.member)
    val conceptsAdded = struct.concepts.map(index.resolveId).map(types.apply).map(_.member)
    val conceptsRemoved = struct.removedConcepts.map(index.resolveId).map(types.apply).map(_.member)

    val localFields = struct.fields.zipWithIndex.map {
      case (f, idx) =>
        val typeReference = toRef(f)
        FullField(fname(f), typeReference, Seq(FieldSource(id, typeReference, idx, 0, meta(f.meta))))
    }

    val removedFields = struct.removedFields.map {
      f =>
        BasicField(fname(f), toRef(f))
    }

    for {
      parentFields <- parents.map(structFields(id)).biFlatAggregate.map(addLevel)
      conceptFieldsAdded <- conceptsAdded.map(structFields(id)).biFlatAggregate.map(addLevel)
      /* all the concept fields will be removed
        in case we have `D {- Concept} extends C {+ conceptField: type} extends B { - conceptField: type } extends A { + Concept }` and
        conceptField will be removed from D too
       */
      conceptFieldsRemoved <- conceptsRemoved.map(structFields(id)).biFlatAggregate.map(_.map(_.basic))
      allRemovals = (conceptFieldsRemoved ++ removedFields).toSet
      allAddedFields = parentFields ++ conceptFieldsAdded ++ localFields
      nothingToRemove = allRemovals -- allAddedFields.map(_.basic).toSet
      allFields = merge(allAddedFields.filterNot {
        f => allRemovals.contains(f.basic)
      })
      conflicts = allFields.groupBy(_.name).filter(_._2.size > 1)
      _ <- if (conflicts.nonEmpty) {
        Left(List(ConflictingFields(id, conflicts)))
      } else {
        Right(())
      }
      allParents <- findAllParents(id, parentsIds, parents)
    } yield {

      if (nothingToRemove.nonEmpty) {
        logger.log(T2Warn.NothingToRemove(id, nothingToRemove))
      }

      if (implicitly[ClassTag[T]].runtimeClass == implicitly[ClassTag[IzType.Interface]].runtimeClass) {
        IzType.Interface(id, allFields, parentsIds, allParents, meta(structMeta), struct)
      } else {
        IzType.DTO(id, allFields, parentsIds, allParents, meta(structMeta), struct)
      }
    }
  }

  private def findAllParents(context: IzTypeId, parentsIds: List[IzTypeId], parents: List[IzType]): Either[List[BuilderFail], Set[IzTypeId]] = {
    parents
      .map {
        case structure: IzStructure =>
          Right(structure.allParents)
        case a: IzAlias =>
          a.source match {
            case IzTypeReference.Scalar(id) =>
              findAllParents(context, List(a.id), List(types(id).member))
            case g: IzTypeReference.Generic =>
              Left(List(ParentCannotBeGeneric(context, g)))
          }

        case o =>
          Left(List(ParentTypeExpectedToBeStructure(context, o.id)))
      }
      .biFlatAggregate
      .map(ids => (ids ++ parentsIds).toSet)
  }

  private def merge(fields: Seq[FullField]): Seq[FullField] = {
    fields
      .groupBy(_.name)
      .values
      .toList
      .map {
        case v :: Nil =>
          v
        case v =>
          val merged = v.tail.foldLeft(v.head) {
            case (acc, f) =>
              acc.copy(defined = acc.defined ++ f.defined)
          }

          // here we choose closest definition as the primary one, compatibility will be checked after we finish processing all types
          val sortedDefns = merged.defined.sortBy(defn => (defn.distance, defn.number))
          val closestType = sortedDefns.head.as
          merged.copy(tpe = closestType, defined = sortedDefns)
      }
  }

  private def addLevel(parentFields: Seq[FullField]): Seq[FullField] = {
    parentFields.map {
      f =>
        f.copy(defined = f.defined.map(d => d.copy(distance = d.distance + 1)))
    }
  }

  private def toRef(f: RawField): IzTypeReference = {
    resolve(f.typeId)
  }

  private def fname(f: RawField): FName = {
    def default: String = resolve(f.typeId) match {
      case IzTypeReference.Scalar(id) =>
        id.name.name
      case IzTypeReference.Generic(id, _) =>
        id.name.name
    }

    FName(f.name.getOrElse(default))
  }

  private def structFields(context: IzTypeId)(tpe: IzType): Either[List[BuilderFail], Seq[FullField]] = {
    tpe match {
      case a: IzAlias =>
        a.source match {
          case IzTypeReference.Scalar(id) =>
            structFields(context)(types.apply(id).member)
          case g: IzTypeReference.Generic =>
            Left(List(ParentCannotBeGeneric(context, g)))
        }

      case structure: IzStructure =>
        Right(structure.fields)
      case o =>
        Left(List(ParentTypeExpectedToBeStructure(context, o.id)))
    }
  }

  private def enumMembers(context: IzTypeId)(tpe: IzType): Either[List[BuilderFail], Seq[EnumMember]] = {
    tpe match {
      case a: IzAlias =>
        a.source match {
          case IzTypeReference.Scalar(id) =>
            enumMembers(context)(types.apply(id).member)
          case g: IzTypeReference.Generic =>
            Left(List(EnumExpectedButGotGeneric(context, g)))
        }

      case structure: Enum =>
        Right(structure.members)
      case o =>
        Left(List(EnumExpected(context, o.id)))
    }
  }

  private def meta(meta: RawNodeMeta): NodeMeta = {
    NodeMeta(meta.doc, Seq.empty, meta.position)
  }
}
