package com.github.pshirshov.izumi.distage.model.plan

import com.github.pshirshov.izumi.distage.model.plan.DodgyPlan._
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.{ImportDependency, InstantiationOp}
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._

import scala.collection.mutable


final case class DodgyPlan(
                            operations: OpMap
                            , topology: PlanTopology
                            , issues: mutable.ArrayBuffer[PlanningFailure]
                          )

object DodgyPlan {

  type OpMap = mutable.HashMap[DIKey, InstantiationOp]

  def empty: DodgyPlan = DodgyPlan(
    new OpMap
    , PlanTopology.empty
    , mutable.ArrayBuffer.empty[PlanningFailure]
  )
}


final case class ResolvedSetsPlan(
                                   imports: Map[DIKey, ImportDependency]
                                   , steps: Seq[InstantiationOp]
                                   , issues: Seq[PlanningFailure]
                                 ) {
  def statements: Seq[ExecutableOp] = imports.values.toSeq ++ steps
}

final case class ResolvedCyclesPlan(
                                     imports: Map[DIKey, ImportDependency]
                                     , steps: Seq[ExecutableOp]
                                     , issues: Seq[PlanningFailure]
                                   ) {
  def statements: Seq[ExecutableOp] = imports.values.toSeq ++ steps
}

