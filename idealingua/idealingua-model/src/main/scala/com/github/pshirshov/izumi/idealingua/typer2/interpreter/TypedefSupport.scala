package com.github.pshirshov.izumi.idealingua.typer2.interpreter

import com.github.pshirshov.izumi.idealingua.model.common.TypeName
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns.{RawField, RawNodeMeta, RawStructure, RawTypeDef}
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.typeid._
import com.github.pshirshov.izumi.idealingua.typer2.{DomainIndex, RefRecorder, TsProvider, WarnLogger}
import com.github.pshirshov.izumi.idealingua.typer2.model.{IzType, IzTypeId, IzTypeReference, T2Warn}
import com.github.pshirshov.izumi.idealingua.typer2.model.IzType.{Enum, ForeignGeneric, ForeignScalar, Identifier, Interpolation, IzAlias, IzStructure}
import com.github.pshirshov.izumi.idealingua.typer2.model.IzType.model._
import com.github.pshirshov.izumi.idealingua.typer2.model.IzTypeId.BuiltinType
import com.github.pshirshov.izumi.idealingua.typer2.model.IzTypeId.model.IzNamespace
import com.github.pshirshov.izumi.idealingua.typer2.model.IzTypeReference.model.{IzTypeArgName, RefToTLTLink}
import com.github.pshirshov.izumi.idealingua.typer2.model.T2Fail._
import com.github.pshirshov.izumi.idealingua.typer2.results._

import scala.reflect.ClassTag


trait TypedefSupport {
  //
  def makeIdentifier(i: RawTypeDef.Identifier, subpath: Seq[IzNamespace]): TSingleT[IzType.Identifier]

  def makeIdentifier(id: IzTypeId, i: RawTypeDef.Identifier): TSingleT[IzType.Identifier]

  //
  def makeAlias(a: RawTypeDef.Alias, subpath: Seq[IzNamespace]): TSingleT[IzType.IzAlias]

  def makeAlias(id: IzTypeId, a: RawTypeDef.Alias): TSingleT[IzType.IzAlias]

  //
  def makeEnum(e: RawTypeDef.Enumeration, subpath: Seq[IzNamespace]): TSingle

  def makeEnum(id: IzTypeId, e: RawTypeDef.Enumeration): TSingle


  def makeInterface(id: IzTypeId, i: RawTypeDef.Interface): TSingle

  def makeInterface(i: RawTypeDef.Interface, subpath: Seq[IzNamespace]): TSingle

  //
  def makeDto(id: IzTypeId, i: RawTypeDef.DTO): TSingle

  def makeDto(i: RawTypeDef.DTO, subpath: Seq[IzNamespace]): TSingle

  //
  def makeForeign(v: RawTypeDef.ForeignType): TSingle

  def meta(meta: RawNodeMeta): NodeMeta

  def make[T <: IzStructure : ClassTag](struct: RawStructure, id: IzTypeId, structMeta: RawNodeMeta): TSingle

  def refToTopLevelRef(id: IzTypeReference, requiredNow: Boolean): Either[List[BuilderFail], IzTypeReference]
}

class TypedefSupportImpl(index: DomainIndex, resolvers: Resolvers, context: Interpreter.Args, refRecorder: RefRecorder, logger: WarnLogger, provider: TsProvider) extends TypedefSupport {
  def makeForeign(v: RawTypeDef.ForeignType): TSingle = {
    v.id match {
      case RawTemplateNoArg(name) =>
        val id = index.toId(Seq.empty, index.resolveTopLeveleName(RawDeclaredTypeName(name)))
        val badMappings = v.mapping.values.filter(ctx => ctx.parameters.nonEmpty || ctx.parts.size != 1)

        if (badMappings.isEmpty) {
          Right(ForeignScalar(id, v.mapping.mapValues(_.parts.head), meta(v.meta)))
        } else {
          Left(List(UnexpectedArguments(id, badMappings.toSeq, meta(v.meta))))
        }

      case RawTemplateWithArg(name, args) =>
        val id = index.toId(Seq.empty, index.makeAbstract(RawNongenericRef(Seq.empty, name)))

        val params = args.map(a => IzTypeArgName(a.name))
        Right(ForeignGeneric(id, params, v.mapping.mapValues(ctx => Interpolation(ctx.parts, ctx.parameters.map(IzTypeArgName))), meta(v.meta)))
    }
  }

  def makeIdentifier(i: RawTypeDef.Identifier, subpath: Seq[IzNamespace]): TSingleT[IzType.Identifier] = {
    makeIdentifier(resolvers.nameToId(i.id, subpath), i)
  }

  def makeIdentifier(id: IzTypeId, i: RawTypeDef.Identifier): TSingleT[IzType.Identifier] = {
    for {
      fields <- i.fields.zipWithIndex.map {
        case (f, idx) =>
          toRef(f).map {
            ref =>
              FullField(fname(f), ref, Seq(FieldSource(id, ref, idx, 0, meta(f.meta))))
          }
      }.biAggregate
    } yield {
      Identifier(id, fields, meta(i.meta))
    }
  }


  def makeEnum(e: RawTypeDef.Enumeration, subpath: Seq[IzNamespace]): TSingle = {
    makeEnum(resolvers.nameToId(e.id, subpath), e)
  }

  def makeEnum(id: IzTypeId, e: RawTypeDef.Enumeration): TSingle = {
    val tmeta = meta(e.meta)

    for {
      maybeParents <- e.struct.parents.map(resolvers.resolve).map {
        case IzTypeReference.Scalar(rid) =>
          Right(rid)
        case IzTypeReference.Generic(rid, _, _) =>
          Left(List(ParentTypeExpectedToBeScalar(id, rid, tmeta)))
      }.biAggregate
      parents = maybeParents.map(provider.freeze().apply).map(_.member)
      parentMembers <- parents.map(enumMembers(id, tmeta)).biFlatAggregate
    } yield {
      val localMembers = e.struct.members.map {
        m =>
          EnumMember(m.value, None, meta(m.meta))
      }
      val removedFields = e.struct.removed.toSet
      val allMembers = (parentMembers ++ localMembers).filterNot(m => removedFields.contains(m.name))
      Enum(id, allMembers, tmeta)
    }
  }


  def makeInterface(id: IzTypeId, i: RawTypeDef.Interface): TSingle = {
    val struct = i.struct
    make[IzType.Interface](struct, id, i.meta)
  }

  def makeInterface(i: RawTypeDef.Interface, subpath: Seq[IzNamespace]): TSingle = {
    makeInterface(resolvers.nameToId(i.id, subpath), i)
  }


  def makeDto(id: IzTypeId, i: RawTypeDef.DTO): TSingle = {
    val struct = i.struct
    make[IzType.DTO](struct, id, i.meta)
  }
  def makeDto(i: RawTypeDef.DTO, subpath: Seq[IzNamespace]): TSingle = {
    makeDto(resolvers.nameToId(i.id, subpath), i)
  }


  def makeAlias(a: RawTypeDef.Alias, subpath: Seq[IzNamespace]): TSingleT[IzType.IzAlias] = {
    makeAlias(resolvers.nameToId(a.id, subpath), a)
  }

  def makeAlias(id: IzTypeId, a: RawTypeDef.Alias): TSingleT[IzType.IzAlias] = {
    Right(IzAlias(id, resolvers.resolve(a.target), meta(a.meta)))
  }


  def refToTopLevelRef(id: IzTypeReference, requiredNow: Boolean): Either[List[BuilderFail], IzTypeReference] = {
    resolvers.refToTopId2(id) match {
      case s: IzTypeReference.Scalar =>
        (id, s.id) match {
          case (g: IzTypeReference.Generic, sid: IzTypeId.UserType) =>
            for {
              _ <- if (requiredNow) {
                refRecorder.requireNow(RefToTLTLink(g, sid))
              } else {
                Right(refRecorder.require(RefToTLTLink(g, sid)))
              }
            } yield {
              s
            }
          case _ =>
            Right(s)
        }


      case g@IzTypeReference.Generic(_: BuiltinType, _, _) =>
        Right(g)

      case g: IzTypeReference.Generic =>
        Left(List(TopLevelScalarOrBuiltinGenericExpected(id, g)))
    }
  }

  def refToTopLevelScalarRefNow(id: IzTypeReference): Either[List[BuilderFail], IzTypeReference.Scalar] = {
    refToTopLevelRef(id, requiredNow = true) match {
      case Left(value) =>
        Left(value)
      case Right(value) =>
        value match {
          case s: IzTypeReference.Scalar =>
            Right(s)
          case g: IzTypeReference.Generic =>
            Left(List(TopLevelScalarOrBuiltinGenericExpected(id, g)))
        }
    }
  }


  def make[T <: IzStructure : ClassTag](struct: RawStructure, id: IzTypeId, structMeta: RawNodeMeta): TSingle = {
    val tmeta = meta(structMeta)

    for {
      maybeParentsIds <- struct.interfaces.map(resolvers.resolve).map(refToTopLevelScalarRefNow).biAggregate
      parentsIds = maybeParentsIds.map(_.id)
      parents = parentsIds.map(provider.freeze().apply).map(_.member)

      maybeConceptsAdded <- struct.concepts.map(resolvers.resolve).map(refToTopLevelScalarRefNow).biAggregate
      conceptsAdded = maybeConceptsAdded.map(_.id).map(provider.freeze().apply).map(_.member)

      maybeConceptsRemoved <- struct.removedConcepts.map(resolvers.resolve).map(refToTopLevelScalarRefNow).biAggregate
      conceptsRemoved = maybeConceptsRemoved.map(_.id).map(provider.freeze().apply).map(_.member)

      removedFields <- struct.removedFields.map {
        f =>
          toRef(f).map {
            ref =>
              BasicField(fname(f), ref)

          }
      }.biAggregate


      localFields <- struct.fields.zipWithIndex.map {
        case (f, idx) =>
          toRef(f).map {
            typeReference =>
              FullField(fname(f), typeReference, Seq(FieldSource(id, typeReference, idx, 0, meta(f.meta))))
          }
      }.biAggregate

      parentFields <- parents.map(structFields(id, tmeta)).biFlatAggregate.map(addLevel)
      conceptFieldsAdded <- conceptsAdded.map(structFields(id, tmeta)).biFlatAggregate.map(addLevel)
      /* all the concept fields will be removed
        in case we have `D {- Concept} extends C {+ conceptField: type} extends B { - conceptField: type } extends A { + Concept }` and
        conceptField will be removed from D too
       */
      conceptFieldsRemoved <- conceptsRemoved.map(structFields(id, tmeta)).biFlatAggregate.map(_.map(_.basic))
      allRemovals = (conceptFieldsRemoved ++ removedFields).toSet
      allAddedFields = parentFields ++ conceptFieldsAdded ++ localFields
      nothingToRemove = allRemovals -- allAddedFields.map(_.basic).toSet
      allFields = merge(allAddedFields.filterNot {
        f => allRemovals.contains(f.basic)
      })
      conflicts = allFields.groupBy(_.name).filter(_._2.size > 1)
      _ <- if (conflicts.nonEmpty) {
        Left(List(ConflictingFields(id, conflicts, tmeta)))
      } else {
        Right(())
      }
      allParents <- findAllParents(id, tmeta, parentsIds, parents)
    } yield {

      if (nothingToRemove.nonEmpty) {
        logger.log(T2Warn.MissingFieldsToRemove(id, nothingToRemove, tmeta))
      }

      if (implicitly[ClassTag[T]].runtimeClass == implicitly[ClassTag[IzType.Interface]].runtimeClass) {
        IzType.Interface(id, allFields, parentsIds, allParents, tmeta, struct)
      } else {
        IzType.DTO(id, allFields, parentsIds, allParents, tmeta, struct)
      }
    }
  }

  private def findAllParents(context: IzTypeId, meta: NodeMeta, parentsIds: List[IzTypeId], parents: List[IzType]): Either[List[BuilderFail], Set[IzTypeId]] = {
    parents
      .map {
        case structure: IzStructure =>
          Right(structure.allParents)
        case a: IzAlias =>
          a.source match {
            case IzTypeReference.Scalar(id) =>
              findAllParents(context, meta, List(a.id), List(provider.freeze()(id).member))
            case g: IzTypeReference.Generic =>
              Left(List(ParentCannotBeGeneric(context, g, meta)))
          }

        case o =>
          Left(List(ParentTypeExpectedToBeStructure(context, o.id, meta)))
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

  private def toRef(f: RawField): Either[List[BuilderFail], IzTypeReference] = {
    val reference: IzTypeReference = resolvers.resolve(f.typeId)
    refToTopLevelRef(reference, requiredNow = false)
  }

  private def fname(f: RawField): FName = {
    def default0: String = resolvers.resolve(f.typeId) match {
      case IzTypeReference.Scalar(id) =>
        id.name.name
      case IzTypeReference.Generic(id, _, adhocName) =>
        adhocName.map(_.name).getOrElse(id.name.name)
    }

    def default: TypeName = f.typeId match {
      case ref@RawNongenericRef(_, _) =>
        this.context.templateArgs.get(IzTypeArgName(ref.name)) match {
          case Some(_) =>
            ref.name
          case None =>
            default0
        }

      case RawGenericRef(_, _, _, adhocName) =>
        adhocName.getOrElse(default0)
    }

    import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._
    FName(f.name.getOrElse(default).uncapitalize)
  }

  private def structFields(context: IzTypeId, meta: NodeMeta)(tpe: IzType): Either[List[BuilderFail], Seq[FullField]] = {
    tpe match {
      case a: IzAlias =>
        a.source match {
          case IzTypeReference.Scalar(id) =>
            structFields(context, meta)(provider.freeze()(id).member)
          case g: IzTypeReference.Generic =>
            Left(List(ParentCannotBeGeneric(context, g, meta)))
        }

      case structure: IzStructure =>
        Right(structure.fields)
      case o =>
        Left(List(ParentTypeExpectedToBeStructure(context, o.id, meta)))
    }
  }

  private def enumMembers(context: IzTypeId, meta: NodeMeta)(tpe: IzType): Either[List[BuilderFail], Seq[EnumMember]] = {
    tpe match {
      case a: IzAlias =>
        a.source match {
          case IzTypeReference.Scalar(id) =>
            enumMembers(context, meta)(provider.freeze()(id).member)
          case g: IzTypeReference.Generic =>
            Left(List(EnumExpectedButGotGeneric(context, g, meta)))
        }

      case structure: Enum =>
        Right(structure.members)
      case o =>
        Left(List(EnumExpected(context, o.id, meta)))
    }
  }

  def meta(meta: RawNodeMeta): NodeMeta = {
    NodeMeta(meta.doc, Seq.empty, meta.position)
  }

}
