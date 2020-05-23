package izumi.distage.model.plan.initial

import izumi.distage.model.definition.{Binding, ModuleBase}
import izumi.distage.model.plan.ExecutableOp.{CreateSet, InstantiationOp}
import izumi.distage.model.plan.Roots
import izumi.distage.model.plan.initial.PrePlan.{JustOp, SetOp, TraceableOp}
import izumi.distage.model.reflection.DIKey
import izumi.fundamentals.collections.ImmutableMultiMap
import izumi.fundamentals.platform.language.Quirks
import izumi.fundamentals.platform.language.Quirks._
import izumi.fundamentals.collections.IzCollections._

import scala.collection.mutable

final class PrePlan(
  val definition: ModuleBase,
  val roots: Roots,
) {

  private val ops = new mutable.ArrayBuffer[TraceableOp]()

  def freeze: ImmutableMultiMap[DIKey, TraceableOp] = {
    ops.map(op => op.key -> op).toMultimap
  }

  def size: Int = {
    ops.map(_.key).toSet.size
  }

  def append(b: Binding, op: NextOps): PrePlan = {
    Quirks.discard(b)

    op.provisions.foreach {
      p =>
        ops.append(JustOp(p.target, p, b)).discard()
    }
    op.sets.values.foreach {
      p =>
        ops.append(SetOp(p.target, p, b)).discard()
    }
    this
  }
}

object PrePlan {
  sealed trait TraceableOp {
    def key: DIKey
    def op: InstantiationOp
    def binding: Binding
  }
  final case class JustOp(key: DIKey, op: InstantiationOp, binding: Binding) extends TraceableOp
  final case class SetOp(key: DIKey, op: CreateSet, binding: Binding) extends TraceableOp

  def empty(definition: ModuleBase, roots: Roots): PrePlan = {
    new PrePlan(definition, roots)
  }
}
