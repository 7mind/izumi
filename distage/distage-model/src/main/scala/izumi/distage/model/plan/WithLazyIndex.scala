package izumi.distage.model.plan

import izumi.distage.model.definition.ModuleBase
import izumi.distage.model.reflection.universe.RuntimeDIUniverse._

trait WithLazyIndex[OpType <: ExecutableOp] {
  this: AbstractPlan[OpType] =>
  def index: Map[DIKey, OpType] = lazyIndex
  def definition: ModuleBase = lazyDefn

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

  private[this] final lazy val lazyIndex : Map[DIKey, OpType] = {
    steps.map(s => s.target -> s).toMap
  }

}
