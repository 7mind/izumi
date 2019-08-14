package izumi.distage.model.plan

import izumi.distage.model.GCMode
import izumi.distage.model.definition.{Binding, ModuleBase}
import izumi.distage.model.plan.DodgyPlan._
import izumi.distage.model.plan.ExecutableOp.{CreateSet, InstantiationOp}
import izumi.distage.model.reflection.universe
import izumi.distage.model.reflection.universe.RuntimeDIUniverse._
import izumi.fundamentals.collections.ImmutableMultiMap
import izumi.fundamentals.collections.IzCollections._
import izumi.fundamentals.platform.language.Quirks
import izumi.fundamentals.platform.language.Quirks._

import scala.collection.mutable

final class DodgyPlan(
                       val definition: ModuleBase,
                       val gcMode: GCMode,
                     ) {

  private val ops = new mutable.ArrayBuffer[TraceableOp]()

  def freeze: ImmutableMultiMap[universe.RuntimeDIUniverse.DIKey, TraceableOp] = {
    ops.map(op => op.key -> op).toMultimap
  }

  def size: Int = {
    ops.map(_.key).toSet.size
  }

  def append(b: Binding, op: NextOps): DodgyPlan = {
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

object DodgyPlan {
  sealed trait TraceableOp {
    def key: DIKey
    def op: InstantiationOp
    def binding: Binding
  }
  case class JustOp(key: DIKey, op: InstantiationOp, binding: Binding) extends TraceableOp
  case class SetOp(key: DIKey, op: CreateSet, binding: Binding) extends TraceableOp

  def empty(definition: ModuleBase, gcMode: GCMode): DodgyPlan = {
    new DodgyPlan(definition, gcMode)
  }
}


