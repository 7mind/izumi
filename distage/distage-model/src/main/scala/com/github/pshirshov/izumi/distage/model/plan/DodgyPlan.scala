package com.github.pshirshov.izumi.distage.model.plan

import com.github.pshirshov.izumi.distage.model.definition.ModuleBase
import com.github.pshirshov.izumi.distage.model.plan.DodgyPlan._
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.InstantiationOp
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._

import scala.collection.mutable


final case class DodgyPlan(operations: OpMap
                           , topology: PlanTopology
                           , issues: mutable.ArrayBuffer[PlanningFailure]
                           , definition: ModuleBase
                          )

object DodgyPlan {

  type OpMap = mutable.HashMap[DIKey, InstantiationOp]

  def empty(definition: ModuleBase): DodgyPlan = DodgyPlan(
    new OpMap
    , PlanTopology.empty
    , mutable.ArrayBuffer.empty[PlanningFailure]
     , definition
  )
}


