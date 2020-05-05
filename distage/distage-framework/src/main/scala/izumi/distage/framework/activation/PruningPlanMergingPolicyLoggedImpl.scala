//package izumi.distage.framework.activation
//
//import distage.DIKey
//import izumi.distage.model.plan.ExecutableOp.SemiplanOp
//import izumi.distage.model.plan.initial.PrePlan
//import izumi.distage.model.planning.PlanMergingPolicy.DIKeyConflictResolution
//import izumi.distage.planning.PruningPlanMergingPolicyDefaultImpl
//import izumi.fundamentals.platform.strings.IzString._
//import izumi.logstage.api.IzLogger
//
//class PruningPlanMergingPolicyLoggedImpl(
//  logger: IzLogger
//) extends PruningPlanMergingPolicyDefaultImpl() {
//  override protected def logUntaggedConflicts(key: DIKey, noTags: Set[PrePlan.JustOp]): Unit = {
//    logger.debug(s"Untagged conflicts were filtered out in $key: ${noTags.niceList() -> "filtered conflicts"}")
//  }
//  override protected def logHandleIssues(issues: Map[DIKey, DIKeyConflictResolution.Failed]): Unit = {
//    logger.debug(s"Not enough data to solve conflicts, will try to prune: ${formatIssues(issues) -> "issues"}")
//  }
//  override protected def logPruningSuccesfulResolve(issues: Map[DIKey, DIKeyConflictResolution.Failed], erased: Map[DIKey, Set[SemiplanOp]]): Unit = {
//    logger.debug(s"Pruning strategy successfully resolved ${issues.size -> "conlicts"}, ${erased.size -> "erased"}, ${erased.keys.niceList() -> "erased conflicts"}")
//  }
//}
