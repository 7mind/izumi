package com.github.pshirshov.izumi.idealingua.typer2.interpreter

import com.github.pshirshov.izumi.functional.Renderable
import com.github.pshirshov.izumi.idealingua.model.common.TypeName
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns._
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.typeid._
import com.github.pshirshov.izumi.idealingua.typer2._
import com.github.pshirshov.izumi.idealingua.typer2.interpreter.AdtSupport.AdtMemberProducts
import com.github.pshirshov.izumi.idealingua.typer2.model.IzType.model._
import com.github.pshirshov.izumi.idealingua.typer2.model.IzType.{Adt, BuiltinType, CustomTemplate, DTO, Enum, Foreign, ForeignGeneric, ForeignScalar, Generic, Identifier, Interface, Interpolation, IzAlias, IzStructure}
import com.github.pshirshov.izumi.idealingua.typer2.model.IzTypeId.model._
import com.github.pshirshov.izumi.idealingua.typer2.model.IzTypeReference.model.{IzTypeArg, IzTypeArgName}
import com.github.pshirshov.izumi.idealingua.typer2.model.T2Fail._
import com.github.pshirshov.izumi.idealingua.typer2.model.T2Warn.{MissingBranchesToRemove, MissingParentsToRemove}
import com.github.pshirshov.izumi.idealingua.typer2.model.Typespace2.ProcessedOp
import com.github.pshirshov.izumi.idealingua.typer2.model._
import com.github.pshirshov.izumi.idealingua.typer2.results._

import scala.collection.{immutable, mutable}
import scala.reflect.ClassTag

trait StaticInterpreterContext {
  def index: DomainIndex

  def logger: WarnLogger
}


case class InterpreterContext(types: Map[IzTypeId, ProcessedOp], templateArgs: Map[IzTypeArgName, IzTypeReference])


class InterpreterImpl(scontext: StaticInterpreterContext, context: InterpreterContext) extends Interpreter2 {
  private val resolvers = new ResolversImpl(scontext, context)
  private val adts = new AdtSupport(scontext, context, this, resolvers)

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
            adts.makeAdt(a)
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
    val ref = resolvers.resolve(v.source)

    val template = ref match {
      case IzTypeReference.Scalar(tid) =>
        context.types(tid)
      case IzTypeReference.Generic(tid, _, _) =>
        context.types(tid)
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

    val isub = new InterpreterImpl(scontext, context.copy(types = context.types ++ ephemerals, templateContext))
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
              context.types(tid).member match {
                case generic: Generic =>
                  generic match {
                    case _: IzType.BuiltinGeneric =>
                      ref

                    case g =>
                      val tmpName: RawDeclaredTypeName = genericName(args, g, adhocName, meta(m))
                      val ephemeralId: IzTypeId = resolvers.nameToId(tmpName, Seq.empty)

                      if (!ephemerals.contains(ephemeralId)) {
                        val refArgs: immutable.Seq[IzTypeId] = args.map {
                          arg =>
                            arg.value.ref match {
                              case IzTypeReference.Scalar(aid) =>
                                aid

                              case IzTypeReference.Generic(aid, aargs, adhocName1) =>
                                val g1 = context.types(aid).member match {
                                  case generic: Generic =>
                                    generic
                                  case o =>
                                    fail(s"$aid must not point to generic, but we got $o")
                                }

                                assert(g.args.size == aargs.size)
                                val zaargs = g.args.zip(aargs)

                                instantiateArgs(ephemerals, m)(zaargs)

                                val tmpName1: RawDeclaredTypeName = genericName(aargs, g1, adhocName1, meta(m))
                                val ephemeralId1: IzTypeId = resolvers.nameToId(tmpName1, Seq.empty)
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
      scontext.logger.log(T2Warn.TemplateInstanceNameWillBeGenerated(g.id, tmpName, meta))
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
        val id = this.scontext.index.toId(Seq.empty, this.scontext.index.resolveTopLeveleName(RawDeclaredTypeName(name)))
        val badMappings = v.mapping.values.filter(ctx => ctx.parameters.nonEmpty || ctx.parts.size != 1)

        if (badMappings.isEmpty) {
          Right(ForeignScalar(id, v.mapping.mapValues(_.parts.head), meta(v.meta)))
        } else {
          Left(List(UnexpectedArguments(id, badMappings.toSeq, meta(v.meta))))
        }

      case RawTemplateWithArg(name, args) =>
        val id = this.scontext.index.toId(Seq.empty, this.scontext.index.makeAbstract(RawNongenericRef(Seq.empty, name)))

        val params = args.map(a => IzTypeArgName(a.name))
        Right(ForeignGeneric(id, params, v.mapping.mapValues(ctx => Interpolation(ctx.parts, ctx.parameters.map(IzTypeArgName))), meta(v.meta)))
    }
  }


  def cloneType(v: RawTypeDef.NewType): TList = {
    val id = nameToTopId(v.id)
    val sid = this.scontext.index.resolveRef(v.source)
    val source = this.context.types(sid)
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

  private def modify(context: IzTypeId, source: Seq[AdtMember], meta: NodeMeta, modifiers: Option[RawClone]): Either[List[BuilderFail], Seq[AdtMemberProducts]] = {
    modifiers match {
      case Some(value) =>
        modify(context, source, meta, value)
      case None =>
        Right(source.map(s => AdtMemberProducts(s, List.empty)))
    }
  }

  private def modify(context: IzTypeId, source: Seq[AdtMember], cloneMeta: NodeMeta, modifiers: RawClone): Either[List[BuilderFail], Seq[AdtMemberProducts]] = {
    if (modifiers.removedParents.nonEmpty || modifiers.concepts.nonEmpty || modifiers.removedConcepts.nonEmpty || modifiers.fields.nonEmpty || modifiers.removedFields.nonEmpty || modifiers.interfaces.nonEmpty) {
      Left(List(UnexpectedStructureCloneModifiers(context, cloneMeta)))
    } else {
      val removedMembers = modifiers.removedBranches.map(_.name).toSet
      for {
        addedMembers <- modifiers.branches.map(adts.mapMember(context, Seq.empty)).biAggregate
        mSum = source.map(s => AdtMemberProducts(s, List.empty)) ++ addedMembers
        filtered = mSum.filterNot(m => removedMembers.contains(m.member.name))

      } yield {
        val unexpectedRemovals = removedMembers.diff(mSum.map(_.member.name).toSet)
        if (unexpectedRemovals.nonEmpty) {
          scontext.logger.log(MissingBranchesToRemove(context, unexpectedRemovals, cloneMeta))
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
        scontext.logger.log(MissingParentsToRemove(context, unexpectedRemovals, meta))
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


  def makeIdentifier(i: RawTypeDef.Identifier, subpath: Seq[IzNamespace]): TSingleT[IzType.Identifier] = {
    val id = resolvers.nameToId(i.id, subpath)

    val fields = i.fields.zipWithIndex.map {
      case (f, idx) =>
        val ref = toRef(f)
        FullField(fname(f), ref, Seq(FieldSource(id, ref, idx, 0, meta(f.meta))))
    }
    Right(Identifier(id, fields, meta(i.meta)))
  }


  def makeEnum(e: RawTypeDef.Enumeration, subpath: Seq[IzNamespace]): TSingle = {
    val id = resolvers.nameToId(e.id, subpath)
    val parents = e.struct.parents.map(resolvers.refToTopId).map(context.types.apply).map(_.member)
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


  def makeInterface(i: RawTypeDef.Interface, subpath: Seq[IzNamespace]): TSingle = {
    val struct = i.struct
    val id = resolvers.nameToId(i.id, subpath)
    make[IzType.Interface](struct, id, i.meta)
  }


  def makeDto(i: RawTypeDef.DTO, subpath: Seq[IzNamespace]): TSingle = {
    val struct = i.struct
    val id = resolvers.nameToId(i.id, subpath)
    make[IzType.DTO](struct, id, i.meta)
  }


  def makeAlias(a: RawTypeDef.Alias, subpath: Seq[IzNamespace]): TSingleT[IzType.IzAlias] = {
    val id = resolvers.nameToId(a.id, subpath)
    Right(IzAlias(id, resolvers.resolve(a.target), meta(a.meta)))
  }


  private def nameToTopId(id: RawDeclaredTypeName): IzTypeId = {
    resolvers.nameToId(id, Seq.empty)
  }



  private def make[T <: IzStructure : ClassTag](struct: RawStructure, id: IzTypeId, structMeta: RawNodeMeta): TSingle = {
    val parentsIds = struct.interfaces.map(resolvers.refToTopId)
    val parents = parentsIds.map(context.types.apply).map(_.member)
    val conceptsAdded = struct.concepts.map(resolvers.refToTopId).map(context.types.apply).map(_.member)
    val conceptsRemoved = struct.removedConcepts.map(resolvers.refToTopId).map(context.types.apply).map(_.member)

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
        scontext.logger.log(T2Warn.MissingFieldsToRemove(id, nothingToRemove, tmeta))
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
              findAllParents(context, meta, List(a.id), List(this.context.types(id).member))
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
    resolvers.resolve(f.typeId)
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
            structFields(context, meta)(this.context.types.apply(id).member)
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
            enumMembers(context, meta)(this.context.types.apply(id).member)
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
