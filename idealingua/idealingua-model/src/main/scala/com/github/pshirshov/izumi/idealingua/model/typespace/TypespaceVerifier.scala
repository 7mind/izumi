package com.github.pshirshov.izumi.idealingua.model.typespace

import com.github.pshirshov.izumi.idealingua.model.common.TypeId._
import com.github.pshirshov.izumi.idealingua.model.common.{Builtin, DomainId, TypeId, TypeName}
import com.github.pshirshov.izumi.idealingua.model.exceptions.IDLCyclicInheritanceException
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.Service.DefMethod.{Output, RPCMethod}
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.TypeDef._
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.{AdtMember, SimpleStructure, TypeDef}

import scala.util.{Failure, Success, Try}

final case class FailedTypespace(id: DomainId, issues: List[Issue]) {

  import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._

  override def toString: TypeName = s"Typespace $id has failed verification:\n${issues.mkString("\n").shift(2)}"
}

sealed trait Issue

object Issue {

  final case class PrimitiveAdtMember(t: AdtId, members: List[AdtMember]) extends Issue {
    override def toString: TypeName = s"ADT members can't be primitive (implementation limit): ${members.mkString(", ")}"
  }

  final case class DuplicateEnumElements(t: EnumId, members: List[String]) extends Issue {
    override def toString: TypeName = s"Duplicated enumeration members: ${members.mkString(", ")}"
  }

  final case class DuplicateAdtElements(t: AdtId, members: List[String]) extends Issue {
    override def toString: TypeName = s"Duplicated ADT members: ${members.mkString(", ")}"
  }

  final case class NoncapitalizedTypename(t: TypeId) extends Issue {
    override def toString: TypeName = s"All typenames must start with a capital letter: $t"
  }

  final case class ReservedTypenamePrefix(t: TypeId) extends Issue {
    override def toString: TypeName = s"Typenames can't start with reserved runtime prefixes ${TypespaceVerifier.badNames.mkString(",")}: $t"
  }

  final case class CyclicInheritance(t: TypeId) extends Issue {
    override def toString: TypeName = s"Type is involved into cyclic inheritance: $t"
  }

  final case class MissingDependencies(deps: List[MissingDependency]) extends Issue {
    override def toString: TypeName = s"Missing dependencies: ${deps.mkString(", ")}"
  }

}

sealed trait MissingDependency {
  def missing: TypeId
}

object MissingDependency {

  final case class DepField(definedIn: TypeId, missing: TypeId, tpe: typed.Field) extends MissingDependency {
    override def toString: TypeName = s"[field $definedIn::${tpe.name} :$missing]"
  }

  final case class DepPrimitiveField(definedIn: TypeId, missing: TypeId, tpe: typed.IdField) extends MissingDependency {
    override def toString: TypeName = s"[field $definedIn::${tpe.name} :$missing]"
  }


  final case class DepParameter(definedIn: TypeId, missing: TypeId) extends MissingDependency {
    override def toString: TypeName = s"[param $definedIn::$missing]"
  }

  final case class DepServiceParameter(definedIn: ServiceId, method: String, missing: TypeId) extends MissingDependency {
    override def toString: TypeName = s"[sparam $definedIn::$missing]"
  }


  final case class DepInterface(definedIn: TypeId, missing: InterfaceId) extends MissingDependency {
    override def toString: TypeName = s"[interface $definedIn::$missing]"
  }

  final case class DepAlias(definedIn: TypeId, missing: TypeId) extends MissingDependency {
    override def toString: TypeName = s"[alias $definedIn::$missing]"
  }

}


class TypespaceVerifier(ts: Typespace) {
  def verify(): Seq[Issue] = {
    val basic = Seq(
      checkDuplicateMembers,
      checkPrimitiveAdtMembers,
      checkNamingConventions
    ).flatten

    val cycles = checkCyclicInheritance

    val missing = if (cycles.isEmpty) {
      checkMissingReferences
    } else {
      Seq.empty
    }
    
    basic ++ cycles ++ missing
  }


  private def checkMissingReferences: Seq[Issue] = {
    val typeDependencies = ts.domain.types.flatMap(extractDependencies)

    val serviceDependencies = for {
      service <- ts.types.services.values
      method <- service.methods
    } yield {
      method match {
        case m: RPCMethod =>
          val inDeps = extractDeps(m.signature.input)

          val outDeps = m.signature.output match {
            case t: Output.Singular =>
              Seq(t.typeId)
            case t: Output.Struct =>
              extractDeps(t.struct)
            case t: Output.Algebraic =>
              t.alternatives.map(_.typeId)
          }

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
      Seq(Issue.MissingDependencies(missingTypes.toList))
    } else {
      Seq.empty
    }
  }

  private def checkCyclicInheritance: Seq[Issue] = {
    ts.domain.types.flatMap {
      t =>
        Try(ts.inheritance.allParents(t.id)) match {
          case Success(_) =>
            Seq.empty
          case Failure(_: IDLCyclicInheritanceException) =>
            Seq(Issue.CyclicInheritance(t.id))
          case Failure(f) =>
            throw f
        }
    }
  }

  private def checkNamingConventions: Seq[Issue] = {
    ts.domain.types.flatMap {
      t =>
        val noncapitalized = if (t.id.name.head.isLower) {
          Seq(Issue.NoncapitalizedTypename(t.id))
        } else {
          Seq.empty
        }

        val reserved = if (TypespaceVerifier.badNames.exists(t.id.name.startsWith)) {
          Seq(Issue.ReservedTypenamePrefix(t.id))
        } else {
          Seq.empty
        }
        noncapitalized ++ reserved
    }
  }

  private def checkPrimitiveAdtMembers: Seq[Issue] = {
    ts.domain.types.flatMap {
      case t: Adt =>
        val builtins = t.alternatives.collect {
          case m@AdtMember(_: Builtin, _) =>
            m
          case m@AdtMember(a: AliasId, _) if ts.dealias(a).isInstanceOf[Builtin] =>
            m
        }
        if (builtins.nonEmpty) {
          Seq(Issue.PrimitiveAdtMember(t.id, builtins))
        } else {
          Seq.empty
        }
      case _ =>
        Seq.empty
    }
  }

  private def checkDuplicateMembers: Seq[Issue] = {
    ts.domain.types.flatMap {
      case t: Enumeration =>
        val duplicates = t.members.groupBy(v => v).filter(_._2.lengthCompare(1) > 0)
        if (duplicates.nonEmpty) {
          Seq(Issue.DuplicateEnumElements(t.id, duplicates.keys.toList))
        } else {
          Seq.empty
        }

      case t: Adt =>
        val duplicates = t.alternatives.groupBy(v => v.name).filter(_._2.lengthCompare(1) > 0)
        if (duplicates.nonEmpty) {
          Seq(Issue.DuplicateAdtElements(t.id, duplicates.keys.toList))
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

}

object TypespaceVerifier {
  final val badNames = Set("Iz", "IRT", "IDL")
}
