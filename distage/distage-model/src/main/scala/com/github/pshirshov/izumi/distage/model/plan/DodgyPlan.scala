package com.github.pshirshov.izumi.distage.model.plan

import com.github.pshirshov.izumi.distage.model.plan.DodgyPlan._
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.{ImportDependency, InstantiationOp}
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._

import scala.collection.mutable

final case class DodgyPlan(
                            dependees: ReverseDepMap
                            , dependencies: DepMap
                            , operations: OpMap
                            , issues: mutable.ArrayBuffer[PlanningFailure]
                          )

object DodgyPlan {
  type ReverseDepMap = mutable.HashMap[DIKey, mutable.Set[DIKey]] with mutable.MultiMap[DIKey, DIKey]
  type DepMap = mutable.HashMap[DIKey, mutable.Set[DIKey]] with mutable.MultiMap[DIKey, DIKey]
  type OpMap = mutable.HashMap[DIKey, InstantiationOp]

  def empty: DodgyPlan = DodgyPlan(
    new mutable.HashMap[DIKey, mutable.Set[DIKey]] with mutable.MultiMap[DIKey, DIKey]
    , new mutable.HashMap[DIKey, mutable.Set[DIKey]] with mutable.MultiMap[DIKey, DIKey]
    , new OpMap
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

