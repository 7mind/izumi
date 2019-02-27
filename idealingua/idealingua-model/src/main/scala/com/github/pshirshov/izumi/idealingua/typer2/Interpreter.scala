package com.github.pshirshov.izumi.idealingua.typer2

import com.github.pshirshov.izumi.idealingua.model.common._
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns.{RawField, RawNodeMeta, RawStructure, RawTypeDef}
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.typeid.ParsedId
import com.github.pshirshov.izumi.idealingua.typer2.IzType.{Basic, BuiltinType, DTO, Enum, EnumMember, FName, Field2, Foreign, ForeignGeneric, ForeignScalar, Generic, Identifier, Interface, Interpolation, IzAlias, IzStructure, NodeMeta, TODO}
import com.github.pshirshov.izumi.idealingua.typer2.IzTypeId.{IzDomainPath, IzName, IzNamespace, IzPackage}
import com.github.pshirshov.izumi.idealingua.typer2.IzTypeReference.{IzTypeArg, IzTypeArgName, IzTypeArgValue}
import com.github.pshirshov.izumi.idealingua.typer2.T2Fail.InterpretationFail
import com.github.pshirshov.izumi.idealingua.typer2.Typer2.UnresolvedName

import scala.reflect.ClassTag

class Interpreter(_index: DomainIndex, types: Map[IzTypeId, ProcessedOp]) {
  private val index: DomainIndex = _index

  def makeForeign(v: RawTypeDef.ForeignType): Either[List[InterpretationFail], IzType] = {
    v.id match {
      case IndefiniteGeneric(pkg, name, args) =>
        assert(args.forall(_.pkg.isEmpty))
        val id = toId(Seq.empty, index.makeAbstract(IndefiniteId(pkg, name)))
        val params = args.map(a => IzTypeArgName(a.name))
        Right(ForeignGeneric(id, params, v.mapping.mapValues(ctx => Interpolation(ctx.parts, ctx.parameters.map(IzTypeArgName))), meta(v.meta)))

      case _ =>
        val id = toId(Seq.empty, index.makeAbstract(v.id))
        assert(v.mapping.values.forall(ctx => ctx.parameters.isEmpty && ctx.parts.size == 1))
        Right(ForeignScalar(id, v.mapping.mapValues(_.parts.head), meta(v.meta)))
    }
  }

  def makeAdt(a: RawTypeDef.Adt): Either[List[InterpretationFail], IzType] = {
    Right(TODO(toId(a.id)))
  }

  def cloneType(v: RawTypeDef.NewType): Either[List[InterpretationFail], IzType] = {
    val id = resolveId(v.id)
    val sid = resolveId(v.source)
    val source = types(sid)
    val copy = source.member match {
      case _: TsMember.Namespace =>
        ???
      case t: TsMember.UserType =>
        t.tpe match {
          case _: Generic =>
            ???
          case _: Foreign =>
            ???
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
    val id = toId(i.id)

    val fields = i.fields.map {
      f =>
        Field2(fname(f), toRef(f), Seq(id), meta(f.meta))
    }
    Right(Identifier(id, fields, meta(i.meta)))
  }

  def makeEnum(e: RawTypeDef.Enumeration): Either[List[InterpretationFail], IzType.Enum] = {
    val id = toId(e.id)
    val parents = e.struct.parents.map(toId).map(types.apply).map(_.member)
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
    val struct = i.struct
    val id = toId(i.id)
    Right(make[IzType.Interface](struct, id, i.meta))
  }

  def makeDto(i: RawTypeDef.DTO): Either[List[InterpretationFail], IzType.DTO] = {
    val struct = i.struct
    val id = toId(i.id)
    Right(make[IzType.DTO](struct, id, i.meta))
  }

  def makeAlias(a: RawTypeDef.Alias): Either[List[InterpretationFail], IzType.IzAlias] = {
    Right(IzAlias(toId(a.id), resolve(a.target), meta(a.meta)))
  }

  private def resolveId(id: ParsedId): IzTypeId = {
    resolveId(id.toIndefinite)
  }

  private def resolveId(id: AbstractNongeneric): IzTypeId = {
    val unresolved = index.makeAbstract(id)
    val out = IzTypeId.UserType(TypePrefix.UserTLT(makePkg(unresolved)), IzName(unresolved.name))
    out
  }

  private def resolve(id: AbstractIndefiniteId): IzTypeReference = {
    id match {
      case nongeneric: AbstractNongeneric =>
        IzTypeReference.Scalar(resolveId(nongeneric))

      case generic: IndefiniteGeneric =>
        // this is not good
        val id = resolveId(IndefiniteId(generic.pkg, generic.name))
        IzTypeReference.Generic(id, generic.args.zipWithIndex.map {
          case (a, idx) =>
            val argValue = resolve(a)
            IzTypeArg(IzTypeArgName(idx.toString), IzTypeArgValue(argValue))
        })
    }
  }

  private def makePkg(unresolved: UnresolvedName): IzPackage = {
    IzPackage(unresolved.pkg.map(IzDomainPath))
  }

  private def toId(id: TypeId): IzTypeId = {
    val namespace = id.path.within.map(IzNamespace)
    val unresolvedName = index.makeAbstract(id)
    toId(namespace, unresolvedName)
  }


  private def toId(namespace: Seq[IzNamespace], unresolvedName: UnresolvedName): IzTypeId.UserType = {
    val pkg = makePkg(unresolvedName)
    if (namespace.isEmpty) {
      IzTypeId.UserType(TypePrefix.UserTLT(pkg), IzName(unresolvedName.name))
    } else {
      IzTypeId.UserType(TypePrefix.UserT(pkg, namespace), IzName(unresolvedName.name))
    }
  }

  private def make[T <: IzStructure : ClassTag](struct: RawStructure, id: IzTypeId, structMeta: RawNodeMeta): T = {
    val parentsIds = struct.interfaces.map(toId)
    val parents = parentsIds.map(types.apply).map(_.member)
    val `+concepts` = struct.concepts.map(resolveId).map(types.apply).map(_.member)
    val `-concepts` = struct.removedConcepts.map(resolveId).map(types.apply).map(_.member)

    val parentFields = parents.flatMap(structFields)
    val `+conceptFields` = `+concepts`.flatMap(structFields)
    val `-conceptFields` = `-concepts`.flatMap(structFields).map(_.basic)

    val localFields = struct.fields.map {
      f =>
        Field2(fname(f), toRef(f), Seq(id), meta(f.meta))
    }

    val removedFields = struct.removedFields.map {
      f =>
        Basic(fname(f), toRef(f))
    }

    val allRemovals = (`-conceptFields` ++ removedFields).toSet
    val allFields = (parentFields ++ `+conceptFields` ++ localFields).filterNot {
      f => allRemovals.contains(f.basic)
    }


    (if (implicitly[ClassTag[T]].runtimeClass == implicitly[ClassTag[IzType.Interface]].runtimeClass) {
      IzType.Interface(id, allFields, parentsIds, meta(structMeta), struct)
    } else {
      IzType.DTO(id, allFields, parentsIds, meta(structMeta), struct)
    }).asInstanceOf[T]
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
    }
  }

  private def structFields(p: TsMember): Seq[Field2] = {
    p match {
      case TsMember.UserType(tpe, _) =>
        structFields(tpe)
      case TsMember.Namespace(_, _) =>
        ???
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
    }
  }

  private def enumMembers(p: TsMember): Seq[EnumMember] = {
    p match {
      case TsMember.UserType(tpe, _) =>
        enumMembers(tpe)
      case TsMember.Namespace(_, _) =>
        ???
    }
  }


  private def meta(meta: RawNodeMeta): NodeMeta = {
    IzType.NodeMeta(meta.doc, Seq.empty, meta.position)
  }
}
