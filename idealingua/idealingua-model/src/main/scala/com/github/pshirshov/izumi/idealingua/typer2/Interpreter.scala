package com.github.pshirshov.izumi.idealingua.typer2

import com.github.pshirshov.izumi.idealingua.model.common._
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns.RawAdt.Member
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns.{RawField, RawNodeMeta, RawStructure, RawTypeDef}
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.typeid.ParsedId
import com.github.pshirshov.izumi.idealingua.typer2.model.IzType.model._
import com.github.pshirshov.izumi.idealingua.typer2.model.IzType.{Adt, AdtMemberNested, AdtMemberRef, BuiltinType, DTO, Enum, Foreign, ForeignGeneric, ForeignScalar, Generic, Identifier, Interface, Interpolation, IzAlias, IzStructure}
import com.github.pshirshov.izumi.idealingua.typer2.model.IzTypeId.model.IzNamespace
import com.github.pshirshov.izumi.idealingua.typer2.model.IzTypeReference.model.{IzTypeArg, IzTypeArgName, IzTypeArgValue}
import com.github.pshirshov.izumi.idealingua.typer2.model.T2Fail.InterpretationFail
import com.github.pshirshov.izumi.idealingua.typer2.model.{IzType, IzTypeId, IzTypeReference}

import scala.reflect.ClassTag

class Interpreter(_index: DomainIndex, types: Map[IzTypeId, ProcessedOp]) {
  private val index: DomainIndex = _index

  def makeForeign(v: RawTypeDef.ForeignType): Either[List[InterpretationFail], IzType] = {
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

  def makeAdt(a: RawTypeDef.Adt): Either[List[InterpretationFail], IzType] = {
    makeAdt(a, Seq.empty)
  }

  private def makeAdt(a: RawTypeDef.Adt, subpath: Seq[IzNamespace]): Either[List[InterpretationFail], IzType] = {
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
        AdtMemberRef(name, tpe, meta(m))
      case Member.NestedDefn(nested) =>
        val tpe = nested match {
          case n: RawTypeDef.Interface =>
            makeInterface(n, subpath)
          case n: RawTypeDef.DTO =>
            makeDto(n, subpath)
          case n: RawTypeDef.Enumeration =>
            makeEnum(n, subpath)
          case n: RawTypeDef.Alias =>
            makeAlias(n, subpath)
          case n: RawTypeDef.Identifier =>
            makeIdentifier(n, subpath)
          case n: RawTypeDef.Adt =>
            makeAdt(n, subpath :+ IzNamespace(n.id.name))
        }
        tpe match {
          case Left(_) =>
            ???
          case Right(value) =>
            AdtMemberNested(value.id.name.name, value, meta(nested.meta))
        }

    }
    Right(Adt(id, members, meta(a.meta)))
  }

  def cloneType(v: RawTypeDef.NewType): Either[List[InterpretationFail], IzType] = {
    val id = resolveId(v.id)
    val sid = index.resolveId(v.source)
    val source = types(sid)
    val copy = source.member match {
      case t: TsMember.UserType =>
        t.tpe match {
          case builtinType: BuiltinType =>
            assert(v.modifiers.isEmpty)
            IzAlias(id, IzTypeReference.Scalar(builtinType.id), meta(v.meta))

          case a: IzAlias =>
            assert(v.modifiers.isEmpty)
            a.copy(id = id)

          case d: DTO =>
            val modified = modify(d, v.modifiers)
            make[DTO](modified, id, v.meta)

          case d: Interface =>
            val modified = modify(d, v.modifiers)
            make[Interface](modified, id, v.meta)

          case i: Identifier =>
            assert(v.modifiers.isEmpty)
            i.copy(id = id)

          case e: Enum =>
            assert(v.modifiers.isEmpty)
            e.copy(id = id)
          case _: Generic =>
            ???
          case _: Foreign =>
            ???
          case _: Adt =>
            ???
        }
    }

    Right(copy)
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


  def makeIdentifier(i: RawTypeDef.Identifier): Either[List[InterpretationFail], IzType] = {
    makeIdentifier(i, Seq.empty)
  }

  private def makeIdentifier(i: RawTypeDef.Identifier, subpath: Seq[IzNamespace]): Either[List[InterpretationFail], IzType] = {
    val id = toId(i.id, subpath)

    val fields = i.fields.zipWithIndex.map {
      case (f, idx) =>
        val ref = toRef(f)
        Field2(fname(f), ref, Seq(FieldSource(id, ref, idx, 0, meta(f.meta))))
    }
    Right(Identifier(id, fields, meta(i.meta)))
  }

  def makeEnum(e: RawTypeDef.Enumeration): Either[List[InterpretationFail], IzType.Enum] = {
    makeEnum(e, Seq.empty)
  }

  private def makeEnum(e: RawTypeDef.Enumeration, subpath: Seq[IzNamespace]): Either[List[InterpretationFail], IzType.Enum] = {
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


  def makeInterface(i: RawTypeDef.Interface): Either[List[InterpretationFail], IzType.Interface] = {
    makeInterface(i, Seq.empty)
  }

  private def makeInterface(i: RawTypeDef.Interface, subpath: Seq[IzNamespace]): Either[List[InterpretationFail], IzType.Interface] = {
    val struct = i.struct
    val id = toId(i.id, subpath)
    Right(make[IzType.Interface](struct, id, i.meta))
  }

  def makeDto(i: RawTypeDef.DTO): Either[List[InterpretationFail], IzType.DTO] = {
    makeDto(i, Seq.empty)
  }

  private def makeDto(i: RawTypeDef.DTO, subpath: Seq[IzNamespace]): Either[List[InterpretationFail], IzType.DTO] = {
    val struct = i.struct
    val id = toId(i.id, subpath)
    Right(make[IzType.DTO](struct, id, i.meta))
  }


  def makeAlias(a: RawTypeDef.Alias): Either[List[InterpretationFail], IzType.IzAlias] = {
    makeAlias(a, Seq.empty)
  }

  private def makeAlias(a: RawTypeDef.Alias, subpath: Seq[IzNamespace]): Either[List[InterpretationFail], IzType.IzAlias] = {
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
    assert(id.path.within.isEmpty)
    val namespace = subpath
    val unresolvedName = index.makeAbstract(id)
    index.toId(namespace, unresolvedName)
  }



  private def make[T <: IzStructure : ClassTag](struct: RawStructure, id: IzTypeId, structMeta: RawNodeMeta): T = {
    val parentsIds = struct.interfaces.map(toTopId)
    val parents = parentsIds.map(types.apply).map(_.member)
    val `+concepts` = struct.concepts.map(index.resolveId).map(types.apply).map(_.member)
    val `-concepts` = struct.removedConcepts.map(index.resolveId).map(types.apply).map(_.member)

    val parentFields = addLevel(parents.flatMap(structFields))
    val `+conceptFields` = addLevel(`+concepts`.flatMap(structFields))
    /* all the concept fields will be removed
      in case we have `D {- Concept} extends C {+ conceptField: type} extends B { - conceptField: type } extends A { + Concept }` and
      conceptField will be removed from D too
     */
    val `-conceptFields` = `-concepts`.flatMap(structFields).map(_.basic)

    val localFields = struct.fields.zipWithIndex.map {
      case (f, idx) =>
        val typeReference = toRef(f)
        Field2(fname(f), typeReference, Seq(FieldSource(id, typeReference, idx, 0, meta(f.meta))))
    }

    val removedFields = struct.removedFields.map {
      f =>
        Basic(fname(f), toRef(f))
    }

    val allRemovals = (`-conceptFields` ++ removedFields).toSet
    val allAddedFields = parentFields ++ `+conceptFields` ++ localFields
    val nothingToRemove = allRemovals -- allAddedFields.map(_.basic).toSet
    if (nothingToRemove.nonEmpty) {
      println(s"Unexpected removals: $nothingToRemove")
    }
    val allFields = merge(allAddedFields.filterNot {
      f => allRemovals.contains(f.basic)
    })
    assert(allFields.groupBy(_.name).forall(_._2.size == 1))
    val allParents = findAllParents(parentsIds, parents)

    (if (implicitly[ClassTag[T]].runtimeClass == implicitly[ClassTag[IzType.Interface]].runtimeClass) {
      IzType.Interface(id, allFields, parentsIds, allParents, meta(structMeta), struct)
    } else {
      IzType.DTO(id, allFields, parentsIds, allParents, meta(structMeta), struct)
    }).asInstanceOf[T]
  }

  private def findAllParents(parentsIds: List[IzTypeId], parents: List[TsMember]): Set[IzTypeId] = {
    (parentsIds ++ parents.flatMap {
      case TsMember.UserType(tpe) =>
        tpe match {
          case structure: IzStructure =>
            structure.allParents
          case a: IzAlias =>
            a.source match {
              case IzTypeReference.Scalar(id) =>
                findAllParents(List(a.id), List(types(id).member))
              case _: IzTypeReference.Generic =>
                ???
            }


          case _ =>
            println(("???", tpe))
            ???
        }
    }).toSet
  }

  private def merge(fields: Seq[Field2]): Seq[Field2] = {
    fields
      .groupBy(_.name)
      .values
      .toList
      .map {
        case v :: Nil  =>
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

  private def addLevel(parentFields: Seq[Field2]): Seq[Field2] = {
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

  private def structFields(tpe: IzType): Seq[Field2] = {
    tpe match {
      case a: IzAlias =>
        a.source match {
          case IzTypeReference.Scalar(id) =>
            structFields(types.apply(id).member)
          case _: IzTypeReference.Generic =>
            ???
        }

      case structure: IzStructure =>
        structure.fields
      case _: Generic =>
        ???
      case _: BuiltinType =>
        ???
      case _: Identifier =>
        ???
      case _: Enum =>
        ???
      case _: Foreign =>
        ???
      case _: Adt =>
        ???
    }
  }

  private def structFields(p: TsMember): Seq[Field2] = {
    p match {
      case TsMember.UserType(tpe) =>
        structFields(tpe)
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

  private def enumMembers(p: TsMember): Seq[EnumMember] = {
    p match {
      case TsMember.UserType(tpe) =>
        enumMembers(tpe)
    }
  }


  private def meta(meta: RawNodeMeta): NodeMeta = {
    NodeMeta(meta.doc, Seq.empty, meta.position)
  }
}
