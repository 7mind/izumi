package izumi.idealingua.model.typespace.verification.rules

import izumi.idealingua.model.common.{Builtin, TypeId}
import izumi.idealingua.model.il.ast.typed.DefMethod.{Output, RPCMethod}
import izumi.idealingua.model.il.ast.typed.TypeDef._
import izumi.idealingua.model.il.ast.typed.{SimpleStructure, TypeDef}
import izumi.idealingua.model.problems.{IDLCyclicInheritanceException, IDLDiagnostics, TypespaceError}
import izumi.idealingua.model.typespace.Typespace
import izumi.idealingua.model.typespace.verification.{MissingDependency, VerificationRule}

import scala.util.{Failure, Success, Try}


object CyclicInheritanceRule extends VerificationRule {
  override def verify(ts: Typespace): IDLDiagnostics = IDLDiagnostics {
    val cycles = ts.domain.types.flatMap {
      t =>
        Try(ts.inheritance.allParents(t.id)) match {
          case Success(_) =>
            Seq.empty
          case Failure(_: IDLCyclicInheritanceException) =>
            Seq(TypespaceError.CyclicInheritance(t.id))
          case Failure(f) =>
            throw f
        }
    }

    if (cycles.isEmpty) {
      new MissingReferencesRule(ts).checkMissingReferences()
    } else {
      List.empty
    }
  }

  class MissingReferencesRule(ts: Typespace) {
    def checkMissingReferences(): Seq[TypespaceError] = {
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
        .filterNot(d => ts.transitivelyReferenced.get(d.missing.path.domain).exists(t => t.types.index.contains(d.missing)))

      if (missingTypes.nonEmpty) {
        Seq(TypespaceError.MissingDependencies(missingTypes.toList))
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


}
