package com.github.pshirshov.izumi.distage.model.plan

import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.{CreateSet, ImportDependency, InstantiationOp}
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse

final case class DodgyPlan(
                            imports: Map[RuntimeDIUniverse.DIKey, ImportDependency]
                            , sets: Set[CreateSet]
                            , steps: Seq[InstantiationOp]
                            , issues: Seq[PlanningFailure]
                          ) {
  //def statements: Seq[ExecutableOp] = imports.values.toSeq ++ sets.toStream ++ steps
  //
  //  def index: Map[RuntimeDIUniverse.DIKey, ExecutableOp] = statements.map(s => s.target -> s).toMap
  //  def keys: Set[RuntimeDIUniverse.DIKey] = statements.map(_.target).toSet
  //
  //  override def toString: String = {
  //    val repr = issues.map(_.toString) ++ statements.map(_.format)
  //    repr.mkString("\n")
  //  }

}

object DodgyPlan {
  def empty: DodgyPlan = DodgyPlan(Map.empty, Set.empty, Seq.empty, Seq.empty)
}


final case class ResolvedSetsPlan(
                                   imports: Map[RuntimeDIUniverse.DIKey, ImportDependency]
                                   , steps: Seq[InstantiationOp]
                                   , issues: Seq[PlanningFailure]
                                 ) {
  def statements: Seq[ExecutableOp] = imports.values.toSeq ++ steps
}

final case class ResolvedCyclesPlan(
                                   imports: Map[RuntimeDIUniverse.DIKey, ImportDependency]
                                   , steps: Seq[ExecutableOp]
                                   , issues: Seq[PlanningFailure]
                                 ) {
  def statements: Seq[ExecutableOp] = imports.values.toSeq ++ steps
}

