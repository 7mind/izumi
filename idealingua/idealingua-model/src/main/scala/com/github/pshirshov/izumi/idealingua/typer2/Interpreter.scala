package com.github.pshirshov.izumi.idealingua.typer2

import com.github.pshirshov.izumi.functional.Renderable
import com.github.pshirshov.izumi.idealingua.model.common.TypeName
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns.RawAdt.Member
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns._
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.typeid._
import com.github.pshirshov.izumi.idealingua.typer2.model.IzType.model._
import com.github.pshirshov.izumi.idealingua.typer2.model.IzType.{Adt, BuiltinType, CustomTemplate, DTO, Enum, Foreign, ForeignGeneric, ForeignScalar, Generic, Identifier, Interface, Interpolation, IzAlias, IzStructure}
import com.github.pshirshov.izumi.idealingua.typer2.model.IzTypeId.model._
import com.github.pshirshov.izumi.idealingua.typer2.model.IzTypeReference.model.{IzTypeArg, IzTypeArgName, IzTypeArgValue}
import com.github.pshirshov.izumi.idealingua.typer2.model.T2Fail._
import com.github.pshirshov.izumi.idealingua.typer2.model.T2Warn.{MissingBranchesToRemove, MissingParentsToRemove}
import com.github.pshirshov.izumi.idealingua.typer2.model.Typespace2.ProcessedOp
import com.github.pshirshov.izumi.idealingua.typer2.model._

import scala.collection.{immutable, mutable}
import scala.reflect.ClassTag


class Interpreter(_index: DomainIndex, types: Map[IzTypeId, ProcessedOp], templateArgs: Map[IzTypeArgName, IzTypeReference], logger: WarnLogger) {
  private val index: DomainIndex = _index

  def dispatch(defn: RawTopLevelDefn.TypeDefn): TList = {
    defn match {
      case RawTopLevelDefn.TLDBaseType(v) =>
        v match {
          case i: RawTypeDef.Interface =>
            makeInterface(i).asList

          case d: RawTypeDef.DTO =>
            makeDto(d).asList

          case a: RawTypeDef.Alias =>
            makeAlias(a).asList

          case e: RawTypeDef.Enumeration =>
            makeEnum(e).asList

          case i: RawTypeDef.Identifier =>
            makeIdentifier(i).asList

          case a: RawTypeDef.Adt =>
            makeAdt(a)
        }

      case t: RawTopLevelDefn.TLDTemplate =>
        makeTemplate(t.v)

      case t: RawTopLevelDefn.TLDInstance =>
        makeInstance(t.v)

      case c: RawTopLevelDefn.TLDNewtype =>
        cloneType(c.v)

      case RawTopLevelDefn.TLDForeignType(v) =>
        makeForeign(v).asList
    }
  }

  def makeInstance(v: RawTypeDef.Instance): TList = {
    val ctxInstances = mutable.HashMap[IzTypeId, ProcessedOp]()
    val out = makeInstance(v, ctxInstances).map(i => i ++ ctxInstances.values.map(_.member))
    //    out.foreach {
    //      i =>
    //        import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._
    //        println(s"Done: ${v.id}= ${v.source}: ${i.niceList()}")
    //        println()
    //    }
    out
  }


  def makeInstance(v: RawTypeDef.Instance, ephemerals: mutable.HashMap[IzTypeId, ProcessedOp]): TList = {
    val ref = resolve(v.source)

    val template = ref match {
      case IzTypeReference.Scalar(tid) =>
        types(tid)
      case IzTypeReference.Generic(tid, _, _) =>
        types(tid)
    }

    val t = template match {
      case ProcessedOp.Exported(member: CustomTemplate) =>
        member
      case o =>
        fail(s"$ref must point to a template, but we got $o")
    }

    val targs = ref match {
      case IzTypeReference.Scalar(_) =>
        Seq.empty
      case IzTypeReference.Generic(_, args, _) =>
        args
    }
    assert(t.args.size == targs.size)
    val argsMap = t.args.zip(targs)

    val templateContext = instantiateArgs(ephemerals, v.meta)(argsMap)


    val withFixedId = t.decl match {
      case i: RawTypeDef.Interface =>
        i.copy(id = v.id, meta = v.meta)
      case d: RawTypeDef.DTO =>
        d.copy(id = v.id, meta = v.meta)
      case a: RawTypeDef.Adt =>
        a.copy(id = v.id, meta = v.meta)
    }

    val isub = new Interpreter(_index, types ++ ephemerals, templateContext, logger)
    val instance = isub.dispatch(RawTopLevelDefn.TLDBaseType(withFixedId))
    instance
  }

  private def fail(message: String): Nothing = {
    throw new IllegalStateException(message)
  }

  private def instantiateArgs(ephemerals: mutable.HashMap[IzTypeId, ProcessedOp], m: RawNodeMeta)(targs: Seq[(IzTypeArgName, IzTypeArg)]): Map[IzTypeArgName, IzTypeReference] = {
    def toRawNonGeneric(g: IzTypeId): RawNongenericRef = {
      g match {
        case IzTypeId.BuiltinType(name) =>
          RawNongenericRef(Seq.empty, name.name)
        case IzTypeId.UserType(prefix, name) =>
          prefix match {
            case TypePrefix.UserTLT(location) =>
              RawNongenericRef(location.path.map(_.name), name.name)
            case t: TypePrefix.UserT =>
              fail(s"type $t expected to be a top level one")
          }
      }
    }

    targs
      .map {
        case (name, arg) =>
          val argt = arg.value.ref match {
            case ref: IzTypeReference.Scalar =>
              ref

            case ref@IzTypeReference.Generic(tid, args, adhocName) =>
              types(tid).member match {
                case generic: Generic =>
                  generic match {
                    case _: IzType.BuiltinGeneric =>
                      ref

                    case g =>
                      val tmpName: RawDeclaredTypeName = genericName(args, g, adhocName, meta(m))
                      val ephemeralId: IzTypeId = nameToId(tmpName, Seq.empty)

                      if (!ephemerals.contains(ephemeralId)) {
                        val refArgs: immutable.Seq[IzTypeId] = args.map {
                          arg =>
                            arg.value.ref match {
                              case IzTypeReference.Scalar(aid) =>
                                aid

                              case IzTypeReference.Generic(aid, aargs, adhocName1) =>
                                val g1 = types(aid).member match {
                                  case generic: Generic =>
                                    generic
                                  case o =>
                                    fail(s"$aid must not point to generic, but we got $o")
                                }

                                assert(g.args.size == aargs.size)
                                val zaargs = g.args.zip(aargs)

                                instantiateArgs(ephemerals, m)(zaargs)

                                val tmpName1: RawDeclaredTypeName = genericName(aargs, g1, adhocName1, meta(m))
                                val ephemeralId1: IzTypeId = nameToId(tmpName1, Seq.empty)
                                ephemeralId1

                            }
                        }
                          .toList

                        val rawRef = toRawNonGeneric(g.id)
                        val rawRefArgs = refArgs.map(toRawNonGeneric).toList
                        val rawGenericRef = RawGenericRef(rawRef.pkg, rawRef.name, rawRefArgs, None)
                        val i = makeInstance(RawTypeDef.Instance(tmpName, rawGenericRef, m), ephemerals)

                        i match {
                          case Left(value) =>
                            fail(s"ephemeral instantiation failed: $value")
                          case Right(value) =>
                            value.foreach {
                              v =>
                                //println(s"registering instance ${v.id}")

                                assert(!ephemerals.contains(v.id), s"BUG in generic machinery: ephemeral ${v.id} is already registered")
                                ephemerals.put(v.id, ProcessedOp.Exported(v))

                            }

                        }
                      }

                      IzTypeReference.Scalar(ephemeralId)
                  }
                case o =>
                  fail(s"reference $ref must point to generic, but we got $o")
              }
          }

          name -> argt
      }
      .toMap
  }

  private def genericName(args: Seq[IzTypeArg], g: Generic, adhocName: Option[IzName], meta: NodeMeta): RawDeclaredTypeName = {
    import Rendering._

    adhocName.map(n => RawDeclaredTypeName(n.name)).getOrElse {
      val tmpName = s"${g.id.name.name}[${args.map(Renderable[IzTypeArg].render).mkString(",")}]"
      logger.log(T2Warn.TemplateInstanceNameWillBeGenerated(g.id, tmpName, meta))
      RawDeclaredTypeName(tmpName)
    }
  }

  def makeTemplate(t: RawTypeDef.Template): TList = {
    val id = nameToTopId(t.decl.id)
    val badNames = t.arguments.groupBy(_.name).filter(_._2.size > 1)
    assert(badNames.isEmpty, s"Template argument names clashed: $badNames")

    val args1 = t.arguments.map(arg => IzTypeArgName(arg.name))
    Right(List(CustomTemplate(id, args1, t.decl)))
  }

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
            case IzTypeReference.Generic(_, _, _) =>
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
          modified <- modify(id, d, newMeta, v.modifiers)
          product <- make[DTO](modified, id, v.meta).map(t => List(t))
        } yield {
          product
        }

      case d: Interface =>
        for {
          modified <- modify(id, d, newMeta, v.modifiers)
          product <- make[Interface](modified, id, v.meta).map(t => List(t))
        } yield {
          product
        }

      case a: Adt =>
        modify(id, a.members, newMeta, v.modifiers).map(newMembers => List(Adt(id, newMembers.map(_.member), newMeta)) ++ newMembers.flatMap(_.additional))

      case i: Identifier =>
        if (v.modifiers.isEmpty) {
          Right(List(i.copy(id = id)))
        } else {
          Left(List(CannotApplyTypeModifiers(id, i.id, newMeta)))
        }

      case e: Enum =>
        if (v.modifiers.isEmpty) {
          Right(List(e.copy(id = id)))
        } else {
          Left(List(CannotApplyTypeModifiers(id, e.id, newMeta)))
        }

      case builtinType: BuiltinType =>
        if (v.modifiers.isEmpty) {
          Right(List(IzAlias(id, IzTypeReference.Scalar(builtinType.id), newMeta)))
        } else {
          Left(List(CannotApplyTypeModifiers(id, builtinType.id, newMeta)))
        }

      case a: IzAlias =>
        if (v.modifiers.isEmpty) {
          Right(List(a.copy(id = id)))
        } else {
          Left(List(CannotApplyTypeModifiers(id, a.id, newMeta)))
        }

      case g: Generic =>
        if (v.modifiers.isEmpty) {
          g match {
            case fg: ForeignGeneric =>
              Right(List(fg.copy(id = id, meta = newMeta)))

            case ct: CustomTemplate =>
              val newDecl = ct.decl match {
                case i: RawTypeDef.Interface =>
                  i.copy(id = RawDeclaredTypeName(id.name.name), meta = v.meta)
                case d: RawTypeDef.DTO =>
                  d.copy(id = RawDeclaredTypeName(id.name.name), meta = v.meta)
                case a: RawTypeDef.Adt =>
                  a.copy(id = RawDeclaredTypeName(id.name.name), meta = v.meta)
              }
              Right(List(ct.copy(decl = newDecl)))

            case _: IzType.BuiltinGeneric =>
              Left(List(FeatureUnsupported(id, "TODO: Builtin generic cloning is almost meaningless and not supported (yet?)", newMeta)))
          }
        } else {
          Left(List(CannotApplyTypeModifiers(id, g.id, newMeta)))
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
          Left(List(CannotApplyTypeModifiers(id, f.id, newMeta)))
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

  private def modify(context: IzTypeId, source: Seq[AdtMember], meta: NodeMeta, modifiers: Option[RawClone]): Either[List[BuilderFail], Seq[Pair]] = {
    modifiers match {
      case Some(value) =>
        modify(context, source, meta, value)
      case None =>
        Right(source.map(s => Pair(s, List.empty)))
    }
  }

  private def modify(context: IzTypeId, source: Seq[AdtMember], cloneMeta: NodeMeta, modifiers: RawClone): Either[List[BuilderFail], Seq[Pair]] = {
    if (modifiers.removedParents.nonEmpty || modifiers.concepts.nonEmpty || modifiers.removedConcepts.nonEmpty || modifiers.fields.nonEmpty || modifiers.removedFields.nonEmpty || modifiers.interfaces.nonEmpty) {
      Left(List(UnexpectedStructureCloneModifiers(context, cloneMeta)))
    } else {
      val removedMembers = modifiers.removedBranches.map(_.name).toSet
      for {
        addedMembers <- modifiers.branches.map(mapMember(context, Seq.empty)).biAggregate
        mSum = source.map(s => Pair(s, List.empty)) ++ addedMembers
        filtered = mSum.filterNot(m => removedMembers.contains(m.member.name))

      } yield {
        val unexpectedRemovals = removedMembers.diff(mSum.map(_.member.name).toSet)
        if (unexpectedRemovals.nonEmpty) {
          logger.log(MissingBranchesToRemove(context, unexpectedRemovals, cloneMeta))
        }
        filtered
      }
    }
  }

  private def modify(context: IzTypeId, source: IzStructure, meta: NodeMeta, modifiers: Option[RawClone]): Either[List[BuilderFail], RawStructure] = {
    val struct = source.defn
    modifiers match {
      case Some(value) =>
        mergeStructs(context, struct, meta, value)
      case None =>
        Right(struct)
    }
  }

  private def mergeStructs(context: IzTypeId, struct: RawStructure, meta: NodeMeta, modifiers: RawClone): Either[List[BuilderFail], RawStructure] = {
    if (modifiers.branches.nonEmpty || modifiers.removedBranches.nonEmpty) {
      Left(List(UnexpectedAdtCloneModifiers(context, meta)))
    } else {
      val removedIfaces = modifiers.removedParents.toSet
      val ifSum = struct.interfaces ++ modifiers.interfaces
      val newIfaces = ifSum.filterNot(removedIfaces.contains)
      val unexpectedRemovals = removedIfaces.diff(ifSum.toSet)
      if (unexpectedRemovals.nonEmpty) {
        logger.log(MissingParentsToRemove(context, unexpectedRemovals, meta))
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
    val tmeta = meta(e.meta)

    for {
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
        templateArgs.get(IzTypeArgName(ref.name)) match {
          case Some(value) =>
            value
          case None =>
            IzTypeReference.Scalar(refToTopId(ref))
        }


      case RawGenericRef(pkg, name, args, adhocName) =>
        val id = refToTopId(RawNongenericRef(pkg, name))
        val typeArgs = args.map {
          a =>
            val argValue = resolve(a)
            IzTypeArg(IzTypeArgValue(argValue))
        }
        IzTypeReference.Generic(id, typeArgs, adhocName.map(IzName))

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

  private def refToTopId(id: RawRef): IzTypeId = {
    val name = index.makeAbstract(id)
    index.toId(Seq.empty, name)
  }

  private def make[T <: IzStructure : ClassTag](struct: RawStructure, id: IzTypeId, structMeta: RawNodeMeta): TSingle = {
    val parentsIds = struct.interfaces.map(refToTopId)
    val parents = parentsIds.map(types.apply).map(_.member)
    val conceptsAdded = struct.concepts.map(refToTopId).map(types.apply).map(_.member)
    val conceptsRemoved = struct.removedConcepts.map(refToTopId).map(types.apply).map(_.member)

    val localFields = struct.fields.zipWithIndex.map {
      case (f, idx) =>
        val typeReference = toRef(f)
        FullField(fname(f), typeReference, Seq(FieldSource(id, typeReference, idx, 0, meta(f.meta))))
    }

    val removedFields = struct.removedFields.map {
      f =>
        BasicField(fname(f), toRef(f))
    }
    val tmeta = meta(structMeta)

    for {
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
              findAllParents(context, meta, List(a.id), List(types(id).member))
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

  private def toRef(f: RawField): IzTypeReference = {
    resolve(f.typeId)
  }

  private def fname(f: RawField): FName = {
    def default0: String = resolve(f.typeId) match {
      case IzTypeReference.Scalar(id) =>
        id.name.name
      case IzTypeReference.Generic(id, _, adhocName) =>
        adhocName.map(_.name).getOrElse(id.name.name)
    }

    def default: TypeName = f.typeId match {
      case ref@RawNongenericRef(_, _) =>
        templateArgs.get(IzTypeArgName(ref.name)) match {
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
            structFields(context, meta)(types.apply(id).member)
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
            enumMembers(context, meta)(types.apply(id).member)
          case g: IzTypeReference.Generic =>
            Left(List(EnumExpectedButGotGeneric(context, g, meta)))
        }

      case structure: Enum =>
        Right(structure.members)
      case o =>
        Left(List(EnumExpected(context, o.id, meta)))
    }
  }

  private def meta(meta: RawNodeMeta): NodeMeta = {
    NodeMeta(meta.doc, Seq.empty, meta.position)
  }
}
