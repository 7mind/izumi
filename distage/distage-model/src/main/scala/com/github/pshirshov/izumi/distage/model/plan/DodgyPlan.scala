package com.github.pshirshov.izumi.distage.model.plan

import com.github.pshirshov.izumi.distage.model.definition.{Binding, ModuleBase}
import com.github.pshirshov.izumi.distage.model.plan.DodgyPlan._
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.{CreateSet, InstantiationOp}
import com.github.pshirshov.izumi.distage.model.reflection.universe
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._
import com.github.pshirshov.izumi.fundamentals.collections.ImmutableMultiMap
import com.github.pshirshov.izumi.fundamentals.collections.IzCollections._
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks._

import scala.collection.mutable

final class DodgyPlan(
                       val definition: ModuleBase,
                       val roots: Set[DIKey],
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

  def empty(definition: ModuleBase, roots: Set[DIKey]): DodgyPlan = {
    new DodgyPlan(definition, roots)
  }
}


