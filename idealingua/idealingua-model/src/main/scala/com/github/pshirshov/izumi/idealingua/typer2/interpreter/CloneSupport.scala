package com.github.pshirshov.izumi.idealingua.typer2.interpreter

import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns.{RawClone, RawStructure, RawTypeDef}
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.typeid.RawDeclaredTypeName
import com.github.pshirshov.izumi.idealingua.typer2.indexing.DomainIndex
import com.github.pshirshov.izumi.idealingua.typer2.interpreter.AdtSupport.AdtMemberProducts
import com.github.pshirshov.izumi.idealingua.typer2.model.IzType.model.{AdtMember, NodeMeta}
import com.github.pshirshov.izumi.idealingua.typer2.model.IzType.{Adt, BuiltinType, CustomTemplate, DTO, Enum, Foreign, ForeignGeneric, ForeignScalar, Generic, Identifier, Interface, IzAlias, IzStructure, TargetInterface}
import com.github.pshirshov.izumi.idealingua.typer2.model.T2Fail._
import com.github.pshirshov.izumi.idealingua.typer2.model.T2Warn.{MissingBranchesToRemove, MissingParentsToRemove}
import com.github.pshirshov.izumi.idealingua.typer2.model.{IzType, IzTypeId, IzTypeReference}
import com.github.pshirshov.izumi.idealingua.typer2.results._
import com.github.pshirshov.izumi.idealingua.typer2.{TsProvider, WarnLogger}

class CloneSupport(index: DomainIndex,
                   i2: TypedefSupport,
                   resolvers: Resolvers,
                   adts: AdtSupport,
                   logger: WarnLogger,
                   provider: TsProvider,
                  ) {
  def cloneType(v: RawTypeDef.NewType): TList = {
    val id = resolvers.nameToTopId(v.id)
    val sid = index.resolveRef(v.source)
    val source = provider.freeze()(sid)
    val newMeta = i2.meta(v.meta)

    source.member match {
      case d: DTO =>
        for {
          modified <- modify(id, d, newMeta, v.modifiers)
          product <- i2.make[DTO](modified, id, v.meta).map(t => List(t))
        } yield {
          product
        }

      case d: Interface =>
        for {
          modified <- modify(id, d, newMeta, v.modifiers)
          product <- i2.make[Interface](modified, id, v.meta).map(t => List(t))
        } yield {
          product
        }

      case a: Adt =>
        for {
          newMembers <- modify(id, a.members, a.contractDefn, newMeta, v.modifiers)
        } yield {
          List(Adt(newMembers.map(_.member), newMeta, a.contract.copy(id = id), a.contractDefn)) ++ newMembers.flatMap(_.additional)
        }

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
              val targetName = RawDeclaredTypeName(id.name.name)
              ct.decl match {
                case i: RawTypeDef.Interface =>
                  Right(List(ct.copy(decl = i.copy(id = targetName, meta = v.meta))))
                case d: RawTypeDef.DTO =>
                  Right(List(ct.copy(decl = d.copy(id = targetName, meta = v.meta))))
                case a: RawTypeDef.Adt =>
                  Right(List(ct.copy(decl = a.copy(id = targetName, meta = v.meta))))
                case s: RawTypeDef.RawService =>
                  Right(List(ct.copy(decl = s.copy(id = targetName, meta = v.meta))))
                case b: RawTypeDef.RawBuzzer =>
                  Right(List(ct.copy(decl = b.copy(id = targetName, meta = v.meta))))
                case b: RawTypeDef.RawStreams =>
                  Right(List(ct.copy(decl = b.copy(id = targetName, meta = v.meta))))
              }

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

      case i: TargetInterface =>
        if (v.modifiers.isEmpty) {
          i match {
            case b: IzType.Buzzer =>
              Right(List(b.copy(id = id, meta = newMeta)))

            case s: IzType.Service =>
              Right(List(s.copy(id = id, meta = newMeta)))
          }
        } else {
          Left(List(CannotApplyTypeModifiers(id, i.id, newMeta)))
        }
    }

  }

  private def modify(context: IzTypeId, source: Seq[AdtMember], contract: Option[RawStructure], meta: NodeMeta, modifiers: Option[RawClone]): Either[List[BuilderFail], Seq[AdtMemberProducts]] = {
    modifiers match {
      case Some(value) =>
        modify(context, source, contract, meta, value)
      case None =>
        Right(source.map(s => AdtMemberProducts(s, List.empty)))
    }
  }


  private def modify(context: IzTypeId, source: Seq[AdtMember], contract: Option[RawStructure], cloneMeta: NodeMeta, modifiers: RawClone): Either[List[BuilderFail], Seq[AdtMemberProducts]] = {
    if (modifiers.removedParents.nonEmpty || modifiers.concepts.nonEmpty || modifiers.removedConcepts.nonEmpty || modifiers.fields.nonEmpty || modifiers.removedFields.nonEmpty || modifiers.interfaces.nonEmpty) {
      Left(List(UnexpectedStructureCloneModifiers(context, cloneMeta)))
    } else {
      val removedMembers = modifiers.removedBranches.map(_.name).toSet
      for {
        addedMembers <- modifiers.branches.map(adts.mapMember(context, Seq.empty)(contract, _)).biAggregate
        mSum = source.map(s => AdtMemberProducts(s, List.empty)) ++ addedMembers
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
}
