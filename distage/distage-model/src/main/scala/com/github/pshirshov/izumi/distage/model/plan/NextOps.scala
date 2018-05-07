package com.github.pshirshov.izumi.distage.model.plan

import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.SetOp._
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.{ImportDependency, InstantiationOp}
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse

final case class NextOps
(
  imports: Set[ImportDependency]
  , sets: Map[RuntimeDIUniverse.DIKey, CreateSet]
  , provisions: Seq[InstantiationOp]
)
