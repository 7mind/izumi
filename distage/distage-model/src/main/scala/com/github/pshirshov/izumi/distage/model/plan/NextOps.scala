package com.github.pshirshov.izumi.distage.model.plan

import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.{CreateSet, InstantiationOp}
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse

final case class NextOps
(
  sets: Map[RuntimeDIUniverse.DIKey, CreateSet]
  , provisions: Seq[InstantiationOp]
)
