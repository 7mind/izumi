package com.github.pshirshov.izumi.idealingua.model.typespace

import com.github.pshirshov.izumi.idealingua.model.common.TypeId._
import com.github.pshirshov.izumi.idealingua.model.common.{Builtin, TypeId}
import com.github.pshirshov.izumi.idealingua.model.exceptions.IDLCyclicInheritanceException
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.DefMethod.{Output, RPCMethod}
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.TypeDef._
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.{AdtMember, SimpleStructure, TypeDef}
import com.github.pshirshov.izumi.idealingua.model.typespace.TypespaceVerificationIssue.{AmbigiousAdtMember, CyclicUsage}

import scala.collection.mutable
import scala.util.{Failure, Success, Try}

trait VerificationRule {
  def verify(ts: Typespace): Seq[TypespaceVerificationIssue]
}

class TypespaceVerifier(ts: Typespace, rules: Seq[VerificationRule]) {
  def verify(): Seq[TypespaceVerificationIssue] = {
    val basic = Seq(
      checkDuplicateMembers,
      checkPrimitiveAdtMembers,
      checkNamingConventions,
      checkCyclicUsage,
      checkAdtIssues,
    ).flatten

    val additional = rules.flatMap(_.verify(ts))

    val cycles = checkCyclicInheritance

    val missing = if (cycles.isEmpty) {
      checkMissingReferences
    } else {
      Seq.empty
    }

    basic ++ cycles ++ missing ++ additional
  }

  private def checkCyclicUsage: Seq[TypespaceVerificationIssue] = {
    ts.domain.types.flatMap {
      t =>
        val (allFields, foundCycles) = allFieldsOf(t)

        if (allFields.contains(t.id)) {
          Seq(CyclicUsage(t.id, foundCycles))
        } else {
          Seq.empty
        }
    }
  }

  private def allFieldsOf(t: TypeDef): (Set[TypeId], Set[TypeId]) = {
    val allFields = mutable.Set.empty[TypeId]
    val foundCycles = mutable.Set.empty[TypeId]
    extractAllFields(t, allFields, foundCycles)
    (allFields.toSet, foundCycles.toSet)
  }

  private def extractAllFields(definition: TypeDef, deps: mutable.Set[TypeId], foundCycles: mutable.Set[TypeId]): Unit = {
    def checkField(i: TypeId): Unit = {
      val alreadyThere = deps.contains(i)
      if (!alreadyThere) {
        deps += i
        extractAllFields(ts.apply(i), deps, foundCycles)
      } else {
        foundCycles += i
      }
    }

    definition match {
      case _: Enumeration =>
        Seq.empty

      case d: Interface =>
        d.struct.fields.filterNot(_.typeId.isInstanceOf[Builtin]).map(_.typeId).foreach(checkField)
        d.struct.superclasses.interfaces.foreach(i => extractAllFields(ts.apply(i), deps, foundCycles))
        d.struct.superclasses.concepts.foreach(c => extractAllFields(ts.apply(c), deps, foundCycles))

      case d: DTO =>
        d.struct.fields.filterNot(_.typeId.isInstanceOf[Builtin]).map(_.typeId).foreach(checkField)
        d.struct.superclasses.interfaces.foreach(i => extractAllFields(ts.apply(i), deps, foundCycles))
        d.struct.superclasses.concepts.foreach(c => extractAllFields(ts.apply(c), deps, foundCycles))

      case d: Identifier =>
        d.fields.map(_.typeId).filterNot(_.isInstanceOf[Builtin]).foreach(checkField)

      case d: Adt =>
        d.alternatives.map(_.typeId).filterNot(_.isInstanceOf[Builtin]).foreach(checkField)

      case _: Alias =>
        Seq.empty
    }
  }


  private def checkMissingReferences: Seq[TypespaceVerificationIssue] = {
    val typeDependencies = ts.domain.types.flatMap(extractDependencies)

    val serviceDependencies = for {
      service <- ts.types.services.values
      method <- service.methods
    } yield {
      method match {
        case m: RPCMethod =>
          val inDeps = extractDeps(m.signature.input)

          val outDeps = getOutDeps(m.signature.output)

          (inDeps ++ outDeps).map(t => MissingDependency.DepServiceParameter(service.id, m.name, t))
      }
    }

    val allDependencies = typeDependencies ++ serviceDependencies.flatten

    // TODO: very ineffective!
    val missingTypes = allDependencies
      .filterNot(_.missing.isInstanceOf[Builtin])
      .filterNot(d => ts.types.index.contains(d.missing))
      .filterNot(d => ts.referenced.get(d.missing.path.domain).exists(t => t.types.index.contains(d.missing)))

    if (missingTypes.nonEmpty) {
      Seq(TypespaceVerificationIssue.MissingDependencies(missingTypes.toList))
    } else {
      Seq.empty
    }
  }

  private def getOutDeps(out: Output): Seq[TypeId] = {
    out match {
      case t: Output.Singular =>
        Seq(t.typeId)
      case t: Output.Struct =>
        extractDeps(t.struct)
      case t: Output.Algebraic =>
        t.alternatives.map(_.typeId)
      case _: Output.Void =>
        Seq.empty
      case t: Output.Alternative =>
        getOutDeps(t.failure) ++ getOutDeps(t.success)
    }
  }

  private def checkCyclicInheritance: Seq[TypespaceVerificationIssue] = {
    ts.domain.types.flatMap {
      t =>
        Try(ts.inheritance.allParents(t.id)) match {
          case Success(_) =>
            Seq.empty
          case Failure(_: IDLCyclicInheritanceException) =>
            Seq(TypespaceVerificationIssue.CyclicInheritance(t.id))
          case Failure(f) =>
            throw f
        }
    }
  }

  private def checkNamingConventions: Seq[TypespaceVerificationIssue] = {
    ts.domain.types.flatMap {
      t =>
        val singleChar = if (t.id.name.size < 2) {
          Seq(TypespaceVerificationIssue.ShortName(t.id))
        } else {
          Seq.empty
        }

        val noncapitalized = if (t.id.name.head.isLower) {
          Seq(TypespaceVerificationIssue.NoncapitalizedTypename(t.id))
        } else {
          Seq.empty
        }

        val reserved = if (TypespaceVerifier.badNames.exists(t.id.name.startsWith)) {
          Seq(TypespaceVerificationIssue.ReservedTypenamePrefix(t.id))
        } else {
          Seq.empty
        }

        singleChar ++
          noncapitalized ++
          reserved
    }
  }

  private def checkPrimitiveAdtMembers: Seq[TypespaceVerificationIssue] = {
    ts.domain.types.flatMap {
      case t: Adt =>
        val builtins = t.alternatives.collect {
          case m@AdtMember(_: Builtin, _, _) =>
            m
          case m@AdtMember(a: AliasId, _, _) if ts.dealias(a).isInstanceOf[Builtin] =>
            m
        }
        if (builtins.nonEmpty) {
          Seq(TypespaceVerificationIssue.PrimitiveAdtMember(t.id, builtins))
        } else {
          Seq.empty
        }
      case _ =>
        Seq.empty
    }
  }

  private def checkDuplicateMembers: Seq[TypespaceVerificationIssue] = {
    ts.domain.types.flatMap {
      case t: Enumeration =>
        val duplicates = t.members.groupBy(v => v).filter(_._2.lengthCompare(1) > 0)
        if (duplicates.nonEmpty) {
          Seq(TypespaceVerificationIssue.DuplicateEnumElements(t.id, duplicates.keys.map(_.value).toList))
        } else {
          Seq.empty
        }

      case t: Adt =>
        val duplicates = t.alternatives.groupBy(v => v.name).filter(_._2.lengthCompare(1) > 0)
        if (duplicates.nonEmpty) {
          Seq(TypespaceVerificationIssue.DuplicateAdtElements(t.id, duplicates.keys.toList))
        } else {
          Seq.empty
        }

      case _ =>
        Seq.empty
    }
  }

  private def extractDeps(ss: SimpleStructure) = {
    ss.fields.map(f => f.typeId) ++ ss.concepts
  }

  private def extractDependencies(definition: TypeDef): Seq[MissingDependency] = {
    definition match {
      case _: Enumeration =>
        Seq.empty

      case d: Interface =>
        d.struct.superclasses.interfaces.map(i => MissingDependency.DepInterface(d.id, i)) ++
          d.struct.superclasses.concepts.flatMap(c => extractDependencies(ts.apply(c))) ++
          d.struct.fields.map(f => MissingDependency.DepField(d.id, f.typeId, f))

      case d: DTO =>
        d.struct.superclasses.interfaces.map(i => MissingDependency.DepInterface(d.id, i)) ++
          d.struct.superclasses.concepts.flatMap(c => extractDependencies(ts.apply(c))) ++
          d.struct.fields.map(f => MissingDependency.DepField(d.id, f.typeId, f))

      case d: Identifier =>
        d.fields.map(f => MissingDependency.DepPrimitiveField(d.id, f.typeId, f))

      case d: Adt =>
        d.alternatives.map(_.typeId).filterNot(_.isInstanceOf[Builtin]).map(ts.apply).flatMap(extractDependencies)

      case d: Alias =>
        Seq(MissingDependency.DepAlias(d.id, d.target))
    }
  }

  private def checkAdtIssues: Seq[TypespaceVerificationIssue] = ts.domain.types.flatMap(checkAdtConflicts)

  private def checkAdtConflicts(definition: TypeDef): Seq[TypespaceVerificationIssue] = {
    definition match {
      case d: Adt =>
        val seen = mutable.HashMap.empty[TypeId, Int]
        checkAdtConflicts(definition, mutable.HashSet.empty, seen)
        val conflicts = seen.filter(_._2 > 1).keys

        if (conflicts.isEmpty) {
          Seq.empty
        } else {
          Seq(AmbigiousAdtMember(d.id, conflicts.toList))
        }

      case _ => Seq.empty
    }
  }

  private def checkAdtConflicts(definition: TypeDef, visited: mutable.HashSet[TypeId], seen: mutable.HashMap[TypeId, Int]): Unit = definition match {
    case d: Adt =>
      d.alternatives.map(_.typeId).foreach {
        id =>
          seen.put(id, seen.getOrElseUpdate(id, 0) + 1)
      }

      visited.add(d.id)

      d.alternatives.filterNot(a => visited.contains(a.typeId)).filterNot(_.typeId.isInstanceOf[Builtin]).map(v => ts.apply(v.typeId)).foreach {
        defn =>
          checkAdtConflicts(defn, visited, seen)
      }

    case _ =>
  }

}

object TypespaceVerifier {
  final val badNames = Set("Iz", "IRT", "IDL")
}
