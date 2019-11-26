package izumi.idealingua.model.typespace.verification.rules

import izumi.idealingua.model.common.{Builtin, TypeId}
import izumi.idealingua.model.il.ast.typed.TypeDef
import izumi.idealingua.model.il.ast.typed.TypeDef._
import izumi.idealingua.model.problems.IDLDiagnostics
import izumi.idealingua.model.typespace.Typespace
import izumi.idealingua.model.problems.TypespaceError.CyclicUsage
import izumi.idealingua.model.typespace.verification.VerificationRule

import scala.collection.mutable

object CyclicUsageRule extends VerificationRule {
  override def verify(ts: Typespace): IDLDiagnostics = IDLDiagnostics {
    ts.domain.types.flatMap {
      t =>
        val (allFields, foundCycles) = new Queries(ts).allFieldsOf(t)

        if (allFields.contains(t.id)) {
          Seq(CyclicUsage(t.id, foundCycles))
        } else {
          Seq.empty
        }
    }
  }

  class Queries(ts: Typespace) {
    def allFieldsOf(t: TypeDef): (Set[TypeId], Set[TypeId]) = {
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
      }
    }

  }


}
