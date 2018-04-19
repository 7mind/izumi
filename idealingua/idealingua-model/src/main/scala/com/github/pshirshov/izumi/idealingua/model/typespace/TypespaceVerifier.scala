package com.github.pshirshov.izumi.idealingua.model.typespace

import com.github.pshirshov.izumi.idealingua.model.common.TypeId._
import com.github.pshirshov.izumi.idealingua.model.common.{Builtin, TypeId, TypeName}
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.Service.DefMethod.{Output, RPCMethod}
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.TypeDef._
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.{AdtMember, SimpleStructure, TypeDef}

import scala.collection.mutable

sealed trait Issue

object Issue {

  case class PrimitiveAdtMember(t: AdtId, members: List[AdtMember]) extends Issue {
    override def toString: TypeName = s"ADT members can't be primitive (implementation limit): ${members.mkString(", ")}"
  }

  case class DuplicateEnumElements(t: EnumId, members: List[String]) extends Issue {
    override def toString: TypeName = s"Duplicated enumeration members: ${members.mkString(", ")}"
  }

  case class DuplicateAdtElements(t: AdtId, members: List[String]) extends Issue {
    override def toString: TypeName = s"Duplicated ADT members: ${members.mkString(", ")}"
  }

  case class NoncapitalizedTypename(t: TypeId) extends Issue {
    override def toString: TypeName = s"All typenames must start with a capital letter: $t"
  }

  case class ReservedTypenamePrefix(t: TypeId) extends Issue {
    override def toString: TypeName = s"Typenames can't start with reserved runtime prefixes `IRT` or `IZ`: $t"
  }

  case class MissingDependencies(deps: List[MissingDependency]) extends Issue {
    override def toString: TypeName = s"Missing dependencies: ${deps.mkString(", ")}"
  }

}

sealed trait MissingDependency {
  def missing: TypeId
}

object MissingDependency {

  case class DepField(definedIn: TypeId, missing: TypeId, tpe: typed.Field) extends MissingDependency {
    override def toString: TypeName = s"[field $definedIn::${tpe.name} :$missing]"
  }

  case class DepPrimitiveField(definedIn: TypeId, missing: TypeId, tpe: typed.PrimitiveField) extends MissingDependency {
    override def toString: TypeName = s"[field $definedIn::${tpe.name} :$missing]"
  }


  case class DepParameter(definedIn: TypeId, missing: TypeId) extends MissingDependency {
    override def toString: TypeName = s"[param $definedIn::$missing]"
  }

  case class DepServiceParameter(definedIn: ServiceId, method: String, missing: TypeId) extends MissingDependency {
    override def toString: TypeName = s"[sparam $definedIn::$missing]"
  }


  case class DepInterface(definedIn: TypeId, missing: InterfaceId) extends MissingDependency {
    override def toString: TypeName = s"[interface $definedIn::$missing]"
  }

  case class DepAlias(definedIn: TypeId, missing: TypeId) extends MissingDependency {
    override def toString: TypeName = s"[alias $definedIn::$missing]"
  }

}


class TypespaceVerifier(ts: Typespace) {
  def verify(): List[Issue] = {
    val issues = mutable.ArrayBuffer[Issue]()
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
      issues += Issue.MissingDependencies(missingTypes.toList)
    }

    ts.domain.types.foreach {
      case t: TypeDef.Enumeration =>
        val duplicates = t.members.groupBy(v => v).filter(_._2.lengthCompare(1) > 0)
        if (duplicates.nonEmpty) {
          issues += Issue.DuplicateEnumElements(t.id, duplicates.keys.toList)
        }

      case t: TypeDef.Adt =>
        val duplicates = t.alternatives.groupBy(v => v.name).filter(_._2.lengthCompare(1) > 0)
        if (duplicates.nonEmpty) {
          issues += Issue.DuplicateAdtElements(t.id, duplicates.keys.toList)
        }

      case _ =>
    }

    ts.domain.types.foreach {
      case t: TypeDef.Adt =>
        val builtins = t.alternatives.collect {
          case m@AdtMember(_: Builtin, _) =>
            m
          case m@AdtMember(a: AliasId, _) if dealias(a).isInstanceOf[Builtin] =>
            m
        }
        if (builtins.nonEmpty) {
          issues += Issue.PrimitiveAdtMember(t.id, builtins)
        }
      case _ =>
    }

    ts.domain.types.foreach {
      t =>
        if (t.id.name.head.isLower) {
          issues += Issue.NoncapitalizedTypename(t.id)
        }

        if (t.id.name.startsWith("IRT") || t.id.name.startsWith("Iz")) {
          issues += Issue.ReservedTypenamePrefix(t.id)
        }
    }

    issues.toList
  }

  private def dealias(t: TypeId): TypeId = {
    t match {
      case a: AliasId =>
        dealias(ts.apply(a).asInstanceOf[Alias].target)

      case o =>
        o
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
