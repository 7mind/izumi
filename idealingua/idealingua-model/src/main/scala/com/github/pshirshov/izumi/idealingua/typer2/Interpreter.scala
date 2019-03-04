package com.github.pshirshov.izumi.idealingua.typer2

import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns.RawAdt.Member
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns._
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.typeid._
import com.github.pshirshov.izumi.idealingua.typer2.model.IzType.model._
import com.github.pshirshov.izumi.idealingua.typer2.model.IzType.{Adt, BuiltinType, DTO, Enum, Foreign, ForeignGeneric, ForeignScalar, Generic, Identifier, Interface, Interpolation, IzAlias, IzStructure}
import com.github.pshirshov.izumi.idealingua.typer2.model.IzTypeId.model._
import com.github.pshirshov.izumi.idealingua.typer2.model.IzTypeReference.model.{IzTypeArg, IzTypeArgName, IzTypeArgValue}
import com.github.pshirshov.izumi.idealingua.typer2.model.T2Fail._
import com.github.pshirshov.izumi.idealingua.typer2.model.T2Warn.{MissingBranchesToRemove, MissingParentsToRemove}
import com.github.pshirshov.izumi.idealingua.typer2.model.{IzType, IzTypeId, IzTypeReference, T2Warn}

import scala.reflect.ClassTag


class Interpreter(_index: DomainIndex, types: Map[IzTypeId, ProcessedOp], logger: WarnLogger) {
  private val index: DomainIndex = _index

  def makeInstance(v: RawTypeDef.Instance): TList = ???

  def makeTemplate(t: RawTypeDef.Template): TList = ???

  def makeForeign(v: RawTypeDef.ForeignType): TSingle = {
    v.id match {
      case RawTemplateNoArg(name) =>
        val id = index.toId(Seq.empty, index.resolveTopLeveleName(RawDeclaredTypeName(name)))
        val badMappings = v.mapping.values.filter(ctx => ctx.parameters.nonEmpty || ctx.parts.size != 1)

        if (badMappings.isEmpty) {
          Right(ForeignScalar(id, v.mapping.mapValues(_.parts.head), meta(v.meta)))
        } else {
          Left(List(UnexpectedArguments(id, badMappings.toSeq)))
        }

      case RawTemplateWithArg(name, args) =>
        val id = index.toId(Seq.empty, index.makeAbstract(RawNongenericRef(Seq.empty, name)))

        val params = args.map(a => IzTypeArgName(a.name))
        Right(ForeignGeneric(id, params, v.mapping.mapValues(ctx => Interpolation(ctx.parts, ctx.parameters.map(IzTypeArgName))), meta(v.meta)))
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

  def mapMember(context: IzTypeId, subpath: Seq[IzNamespace])(member: Member): Either[List[BuilderFail], Pair] = {
    member match {
      case Member.TypeRef(typeId, memberName, m) =>
        val tpe = resolve(typeId)
        for {
          name <- tpe match {
            case IzTypeReference.Scalar(mid) =>
              Right(memberName.getOrElse(mid.name.name))
            case IzTypeReference.Generic(_, _) =>
              memberName match {
                case Some(value) =>
                  Right(value)
                case None =>
                  Left(List(GenericAdtBranchMustBeNamed(context, typeId, meta(m))))
              }
          }
        } yield {
          Pair(AdtMemberRef(name, tpe, meta(m)), List.empty)
        }

      case Member.NestedDefn(nested) =>
        for {
          tpe <- nested match {
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
        } yield {
          Pair(AdtMemberNested(nested.id.name, IzTypeReference.Scalar(tpe.main.id), meta(nested.meta)), tpe.additional)
        }
    }
  }

  private def makeAdt(a: RawTypeDef.Adt, subpath: Seq[IzNamespace]): TChain = {
    val id = nameToId(a.id, subpath)

    for {
      members <- a.alternatives.map(mapMember(id, subpath)).biAggregate
      adtMembers = members.map(_.member)
      associatedTypes = members.flatMap(_.additional)
    } yield {
      Chain(Adt(id, adtMembers, meta(a.meta)), associatedTypes)
    }
  }


  def cloneType(v: RawTypeDef.NewType): TList = {
    val id = nameToTopId(v.id)
    val sid = index.resolveRef(v.source)
    val source = types(sid)
    val newMeta = meta(v.meta)

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

      case a: Adt =>
        modify(id, a.members, v.modifiers).map(newMembers => List(Adt(id, newMembers.map(_.member), newMeta)) ++ newMembers.flatMap(_.additional))

      case i: Identifier =>
        if (v.modifiers.isEmpty) {
          Right(List(i.copy(id = id)))
        } else {
          Left(List(CannotApplyTypeModifiers(id, i.id)))
        }

      case e: Enum =>
        if (v.modifiers.isEmpty) {
          Right(List(e.copy(id = id)))
        } else {
          Left(List(CannotApplyTypeModifiers(id, e.id)))
        }

      case builtinType: BuiltinType =>
        if (v.modifiers.isEmpty) {
          Right(List(IzAlias(id, IzTypeReference.Scalar(builtinType.id), newMeta)))
        } else {
          Left(List(CannotApplyTypeModifiers(id, builtinType.id)))
        }

      case a: IzAlias =>
        if (v.modifiers.isEmpty) {
          Right(List(a.copy(id = id)))
        } else {
          Left(List(CannotApplyTypeModifiers(id, a.id)))
        }

      case g: Generic =>
        if (v.modifiers.isEmpty) {
          g match {
            case fg: ForeignGeneric =>
              Right(List(fg.copy(id = id, meta = newMeta)))

            case generic: IzType.BuiltinGeneric =>
              Left(List(FeatureUnsupported(id, "TODO: Builtin generic cloning is almost meaningless and not supported yet (we need to support templates first)")))
          }
        } else {
          Left(List(CannotApplyTypeModifiers(id, g.id)))
        }

      case f: Foreign =>
        if (v.modifiers.isEmpty) {
          f match {
            case fs: ForeignScalar =>
              Right(List(fs.copy(id = id, meta = newMeta)))

            case fg: ForeignGeneric =>
              Right(List(fg.copy(id = id, meta = newMeta)))
          }
        } else {
          Left(List(CannotApplyTypeModifiers(id, f.id)))
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

  private def modify(context: IzTypeId, source: Seq[AdtMember], modifiers: Option[RawClone]): Either[List[BuilderFail], Seq[Pair]] = {
    modifiers match {
      case Some(value) =>
        modify(context, source, value)
      case None =>
        Right(source.map(s => Pair(s, List.empty)))
    }
  }

  private def modify(context: IzTypeId, source: Seq[AdtMember], modifiers: RawClone): Either[List[BuilderFail], Seq[Pair]] = {
    if (modifiers.removedParents.nonEmpty || modifiers.concepts.nonEmpty || modifiers.removedConcepts.nonEmpty || modifiers.fields.nonEmpty || modifiers.removedFields.nonEmpty || modifiers.interfaces.nonEmpty) {
      Left(List(UnexpectedStructureCloneModifiers(context)))
    } else {
      val removedMembers = modifiers.removedBranches.map(_.name).toSet
      for {
        addedMembers <- modifiers.branches.map(mapMember(context, Seq.empty)).biAggregate
        mSum = source.map(s => Pair(s, List.empty)) ++ addedMembers
        filtered = mSum.filterNot(m => removedMembers.contains(m.member.name))

      } yield {
        val unexpectedRemovals = removedMembers.diff(mSum.map(_.member.name).toSet)
        if (unexpectedRemovals.nonEmpty) {
          logger.log(MissingBranchesToRemove(context, unexpectedRemovals))
        }
        filtered
      }
    }
  }

  private def modify(context: IzTypeId, source: IzStructure, modifiers: Option[RawClone]): Either[List[BuilderFail], RawStructure] = {
    val struct = source.defn
    modifiers match {
      case Some(value) =>
        mergeStructs(context, struct, value)
      case None =>
        Right(struct)
    }
  }

  private def mergeStructs(context: IzTypeId, struct: RawStructure, modifiers: RawClone): Either[List[BuilderFail], RawStructure] = {
    if (modifiers.branches.nonEmpty || modifiers.removedBranches.nonEmpty) {
      Left(List(UnexpectedAdtCloneModifiers(context)))
    } else {
      val removedIfaces = modifiers.removedParents.toSet
      val ifSum = struct.interfaces ++ modifiers.interfaces
      val newIfaces = ifSum.filterNot(removedIfaces.contains)
      val unexpectedRemovals = removedIfaces.diff(ifSum.toSet)
      if (unexpectedRemovals.nonEmpty) {
        logger.log(MissingParentsToRemove(context, unexpectedRemovals))
      }

      val newConcepts = struct.concepts ++ modifiers.concepts
      val removedConcepts = struct.removedConcepts ++ modifiers.removedConcepts
      val newFields = struct.fields ++ modifiers.removedFields
      val removedFields = struct.removedFields ++ modifiers.removedFields

      Right(struct.copy(
        interfaces = newIfaces,
        concepts = newConcepts,
        removedConcepts = removedConcepts,
        fields = newFields,
        removedFields = removedFields,
      ))
    }
  }


  private def makeIdentifier(i: RawTypeDef.Identifier, subpath: Seq[IzNamespace]): TSingleT[IzType.Identifier] = {
    val id = nameToId(i.id, subpath)

    val fields = i.fields.zipWithIndex.map {
      case (f, idx) =>
        val ref = toRef(f)
        FullField(fname(f), ref, Seq(FieldSource(id, ref, idx, 0, meta(f.meta))))
    }
    Right(Identifier(id, fields, meta(i.meta)))
  }


  private def makeEnum(e: RawTypeDef.Enumeration, subpath: Seq[IzNamespace]): TSingle = {
    val id = nameToId(e.id, subpath)
    val parents = e.struct.parents.map(refToTopId).map(types.apply).map(_.member)

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
    val id = nameToId(i.id, subpath)
    make[IzType.Interface](struct, id, i.meta)
  }


  private def makeDto(i: RawTypeDef.DTO, subpath: Seq[IzNamespace]): TSingle = {
    val struct = i.struct
    val id = nameToId(i.id, subpath)
    make[IzType.DTO](struct, id, i.meta)
  }


  private def makeAlias(a: RawTypeDef.Alias, subpath: Seq[IzNamespace]): TSingleT[IzType.IzAlias] = {
    val id = nameToId(a.id, subpath)
    Right(IzAlias(id, resolve(a.target), meta(a.meta)))
  }

  private def resolve(id: RawRef): IzTypeReference = {
    id match {
      case ref@RawNongenericRef(_, _) =>
        IzTypeReference.Scalar(index.resolveRef(ref))

      case RawGenericRef(pkg, name, args) =>
        // TODO: this is not good
        val id = index.resolveRef(RawNongenericRef(pkg, name))
        IzTypeReference.Generic(id, args.zipWithIndex.map {
          case (a, idx) =>
            val argValue = resolve(a)
            IzTypeArg(IzTypeArgName(idx.toString), IzTypeArgValue(argValue))
        })

    }
  }

  private def nameToTopId(id: RawDeclaredTypeName): IzTypeId = {
    nameToId(id, Seq.empty)
  }

  private def nameToId(id: RawDeclaredTypeName, subpath: Seq[IzNamespace]): IzTypeId = {
    val namespace = subpath
    val unresolvedName = index.resolveTopLeveleName(id)
    index.toId(namespace, unresolvedName)
  }

  private def refToTopId(id: RawNongenericRef): IzTypeId = {
    val name = index.makeAbstract(id)
    index.toId(Seq.empty, name)
  }

  private def make[T <: IzStructure : ClassTag](struct: RawStructure, id: IzTypeId, structMeta: RawNodeMeta): TSingle = {
    val parentsIds = struct.interfaces.map(refToTopId)
    val parents = parentsIds.map(types.apply).map(_.member)
    val conceptsAdded = struct.concepts.map(index.resolveRef).map(types.apply).map(_.member)
    val conceptsRemoved = struct.removedConcepts.map(index.resolveRef).map(types.apply).map(_.member)

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
        logger.log(T2Warn.MissingFieldsToRemove(id, nothingToRemove))
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
