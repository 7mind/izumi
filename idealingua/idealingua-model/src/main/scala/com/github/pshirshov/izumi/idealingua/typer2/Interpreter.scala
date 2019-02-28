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

trait Ret {
  type Result[T] = Either[List[BuilderFail], T]
  type TList = Result[List[IzType]]
  type TSingle = Result[IzType]
  type TSingleT[T <: IzType] = Result[T]

  implicit class TSingleExt(ret: TSingle) {
    def asList: TList = ret.map(v => List(v))
  }

}


class Interpreter(_index: DomainIndex, types: Map[IzTypeId, ProcessedOp], logger: WarnLogger) {
  private val index: DomainIndex = _index

  def makeForeign(v: RawTypeDef.ForeignType): TSingle = {
    v.id match {
      case IndefiniteGeneric(pkg, name, args) =>
        assert(args.forall(_.pkg.isEmpty))
        val id = index.toId(Seq.empty, index.makeAbstract(IndefiniteId(pkg, name)))
        val params = args.map(a => IzTypeArgName(a.name))
        Right(ForeignGeneric(id, params, v.mapping.mapValues(ctx => Interpolation(ctx.parts, ctx.parameters.map(IzTypeArgName))), meta(v.meta)))

      case _ =>
        val id = index.toId(Seq.empty, index.makeAbstract(v.id))
        assert(v.mapping.values.forall(ctx => ctx.parameters.isEmpty && ctx.parts.size == 1))
        Right(ForeignScalar(id, v.mapping.mapValues(_.parts.head), meta(v.meta)))
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
    source.member match {
      case builtinType: BuiltinType =>
        assert(v.modifiers.isEmpty)
        Right(List(IzAlias(id, IzTypeReference.Scalar(builtinType.id), meta(v.meta))))

      case a: IzAlias =>
        assert(v.modifiers.isEmpty)
        Right(List(a.copy(id = id)))

      case d: DTO =>
        val modified = modify(d, v.modifiers)
        make[DTO](modified, id, v.meta).map(t => List(t))

      case d: Interface =>
        val modified = modify(d, v.modifiers)
        make[Interface](modified, id, v.meta).map(t => List(t))

      case i: Identifier =>
        assert(v.modifiers.isEmpty)
        Right(List(i.copy(id = id)))

      case e: Enum =>
        assert(v.modifiers.isEmpty)
        Right(List(e.copy(id = id)))
      case _: Generic =>
        ???
      case _: Foreign =>
        ???
      case _: Adt =>
        ???
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

  private def modify(source: IzType, modifiers: Option[RawStructure]): RawStructure = {
    source match {
      case structure: IzStructure =>
        val struct = structure.defn
        modifiers.map(m => mergeStructs(struct, m)).getOrElse(struct)
      case _ =>
        ???
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


  private def makeEnum(e: RawTypeDef.Enumeration, subpath: Seq[IzNamespace]): TSingleT[IzType.Enum] = {
    val id = toId(e.id, subpath)
    val parents = e.struct.parents.map(toTopId).map(types.apply).map(_.member)
    val parentMembers = parents.flatMap(enumMembers)
    val localMembers = e.struct.members.map {
      m =>
        EnumMember(m.value, None, meta(m.meta))
    }
    val removedFields = e.struct.removed.toSet
    val allMembers = (parentMembers ++ localMembers).filterNot(m => removedFields.contains(m.name))

    Right(Enum(id, allMembers, meta(e.meta)))
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
    Right(IzAlias(toId(a.id, subpath), resolve(a.target), meta(a.meta)))
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

  //  implicit class EitherFolderExt[L, R](result: Seq[Either[L, R]]) {
  //    def xflatten: Either[List[L], List[R]] = {
  //      val bad = result.collect({ case Left(e) => e })
  //      if (bad.isEmpty) {
  //        Right(result.collect({ case Right(r) => r }).toList)
  //      } else {
  //        Left(bad.toList)
  //      }
  //    }
  //  }
  //
  //  implicit class EitherFolderExt1[L, R](result: Seq[Either[L, Traversable[R]]]) {
  //    def xflatten1: Either[List[L], List[R]] = {
  //      val bad = result.collect({ case Left(e) => e })
  //      if (bad.isEmpty) {
  //        Right(result.collect({ case Right(r) => r }).flatten.toList)
  //      } else {
  //        Left(bad.toList)
  //      }
  //    }
  //  }

  implicit class EitherFolderExt2[L, R](result: Seq[Either[List[L], Traversable[R]]]) {
    def xflatten2: Either[List[L], List[R]] = {
      val bad = result.collect({ case Left(e) => e })
      if (bad.isEmpty) {
        Right(result.collect({ case Right(r) => r }).flatten.toList)
      } else {
        Left(bad.flatten.toList)
      }
    }
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
      parentFields <- parents.map(structFields(id)).xflatten2.map(addLevel)
      conceptFieldsAdded <- conceptsAdded.map(structFields(id)).xflatten2.map(addLevel)
      /* all the concept fields will be removed
        in case we have `D {- Concept} extends C {+ conceptField: type} extends B { - conceptField: type } extends A { + Concept }` and
        conceptField will be removed from D too
       */
      conceptFieldsRemoved <- conceptsRemoved.map(structFields(id)).xflatten2.map(_.map(_.basic))
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
    } yield {
      val allParents = findAllParents(parentsIds, parents)

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

  private def findAllParents(parentsIds: List[IzTypeId], parents: List[IzType]): Set[IzTypeId] = {
    (parentsIds ++ parents.flatMap {

      case structure: IzStructure =>
        structure.allParents
      case a: IzAlias =>
        a.source match {
          case IzTypeReference.Scalar(id) =>
            findAllParents(List(a.id), List(types(id).member))
          case _: IzTypeReference.Generic =>
            ???
        }

      case tpe =>
        println(("???", tpe))
        ???
    }
      ).toSet
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

  private def enumMembers(tpe: IzType): Seq[EnumMember] = {
    tpe match {
      case a: IzAlias =>
        a.source match {
          case IzTypeReference.Scalar(id) =>
            enumMembers(types.apply(id).member)
          case _: IzTypeReference.Generic =>
            ???
        }
      case structure: Enum =>
        structure.members
      case _: Generic =>
        ???
      case _: BuiltinType =>
        ???
      case _: Identifier =>
        ???
      case _: IzStructure =>
        ???
      case _: Foreign =>
        ???
      case _: Adt =>
        ???
    }
  }

  private def meta(meta: RawNodeMeta): NodeMeta = {
    NodeMeta(meta.doc, Seq.empty, meta.position)
  }
}
