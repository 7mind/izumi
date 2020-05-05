package izumi.distage.model.plan.impl

import izumi.distage.model.definition.ModuleBase
import izumi.distage.model.plan.operations.OperationOrigin
import izumi.distage.model.plan.{AbstractPlan, ExecutableOp}
import izumi.distage.model.reflection._

private[plan] trait PlanLazyOps[OpType <: ExecutableOp] {
  this: AbstractPlan[OpType] =>

  override final def index: Map[DIKey, OpType] = lazyIndex
  override final def definition: ModuleBase = lazyDefn

  private[this] final lazy val lazyDefn = {
    val userBindings = steps.flatMap {
      op =>
        op.origin match {
          case OperationOrigin.UserBinding(binding) =>
            Seq(binding)
          case _ =>
            Seq.empty
        }
    }.toSet
    ModuleBase.make(userBindings)
  }

  private[this] final lazy val lazyIndex: Map[DIKey, OpType] = {
    steps.map(s => s.target -> s).toMap
  }
}
