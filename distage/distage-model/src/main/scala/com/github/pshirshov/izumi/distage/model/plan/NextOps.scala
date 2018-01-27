package com.github.pshirshov.izumi.distage.model.plan

import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.SetOp._
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.{ImportDependency, InstantiationOp}

case class NextOps(
                    imports: Set[ImportDependency]
                    , sets: Set[CreateSet]
                    , provisions: Seq[InstantiationOp]
                  ) {
  def flatten: Seq[ExecutableOp] = {
    imports.toSeq ++ sets.toSeq ++ provisions
  }
}
