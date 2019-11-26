package izumi.distage.model.plan

import izumi.distage.model.definition.ModuleBase
import izumi.distage.model.reflection.universe.RuntimeDIUniverse._

trait WithLazyIndex {
  this: AbstractPlan =>
  def index: Map[DIKey, ExecutableOp] = lazyIndex
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

  private[this] final lazy val lazyIndex : Map[DIKey, ExecutableOp] = {
    steps.map(s => s.target -> s).toMap
  }

}
