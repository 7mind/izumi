package com.github.pshirshov.izumi.distage.model.plan

import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.{CreateSet, ImportDependency, InstantiationOp}
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse

final case class NextOps
(
  imports: Set[ImportDependency]
  , sets: Map[RuntimeDIUniverse.DIKey, CreateSet]
  , provisions: Seq[InstantiationOp]
)
