package com.github.pshirshov.izumi.distage.planning

import com.github.pshirshov.izumi.distage.model.plan.{ExecutableOp, SemiPlan}
import com.github.pshirshov.izumi.distage.model.planning.PlanningHook
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.{DIKey, SafeType, Tag, Wiring}

class AssignableFromAutoSetHook[T: Tag] extends PlanningHook {
  protected val setElemetType: SafeType = SafeType.get[T]

  protected val setKey: DIKey = DIKey.get[Set[T]]

  override def phase50PreForwarding(plan: SemiPlan): SemiPlan = {
    val newMembers = scala.collection.mutable.ArrayBuffer[DIKey.SetElementKey]()

    val newSteps = plan.steps.flatMap {
      op =>
        val opTargetType = ExecutableOp.instanceType(op)
        if (opTargetType weak_<:< setElemetType) {
          val elementKey = DIKey.SetElementKey(setKey, op.target.tpe, plan.steps.size + newMembers.size)
          newMembers += elementKey
          Seq(op, ExecutableOp.WiringOp.ReferenceKey(elementKey, Wiring.UnaryWiring.Reference(op.target.tpe, op.target, weak = true), op.origin))
        } else if (op.target == setKey) {
          Seq.empty
        } else {
          Seq(op)
        }
    }

    val newSetKeys: scala.collection.immutable.Set[DIKey] = newMembers.toSet
    val newSetOp = ExecutableOp.CreateSet(setKey, setKey.tpe, newSetKeys, None)
    SemiPlan(plan.definition, newSteps :+ newSetOp)
  }

}
