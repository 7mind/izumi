package izumi.distage.model.plan

import izumi.distage.model.plan.ExecutableOp.{CreateSet, InstantiationOp}
import izumi.distage.model.reflection.universe.RuntimeDIUniverse

final case class NextOps(
  sets: Map[RuntimeDIUniverse.DIKey, CreateSet],
  provisions: Seq[InstantiationOp]
)
