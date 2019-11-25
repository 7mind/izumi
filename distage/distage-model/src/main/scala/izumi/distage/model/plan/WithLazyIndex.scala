package izumi.distage.model.plan

import izumi.distage.model.reflection.universe.RuntimeDIUniverse._

trait WithLazyIndex {
  this: AbstractPlan =>
  def index: Map[DIKey, ExecutableOp] = lazyIndex

  private[this] final lazy val lazyIndex : Map[DIKey, ExecutableOp] = {
    steps.map(s => s.target -> s).toMap
  }

}
